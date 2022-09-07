/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.query.dao.legacy

import akka.NotUsed
import akka.persistence.PersistentRepr
import akka.persistence.jdbc.config.ReadJournalConfig
import akka.persistence.jdbc.journal.dao.{ BaseJournalDaoWithReadMessages, H2Compat }
import akka.persistence.jdbc.journal.dao.legacy.{ ByteArrayJournalSerializer, JournalRow }
import akka.persistence.jdbc.query.dao.ReadJournalDao
import akka.persistence.jdbc.query.dao.legacy.TagFilterFlow.perfectlyMatchTag
import akka.persistence.jdbc.serialization.FlowPersistentReprSerializer
import akka.serialization.Serialization
import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Source }
import slick.jdbc.JdbcBackend._
import slick.jdbc.{ GetResult, JdbcProfile }

import scala.collection.immutable._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

trait BaseByteArrayReadJournalDao extends ReadJournalDao with BaseJournalDaoWithReadMessages with H2Compat {
  def db: Database
  val profile: JdbcProfile
  def queries: ReadJournalQueries
  def serializer: FlowPersistentReprSerializer[JournalRow]
  def readJournalConfig: ReadJournalConfig

  import profile.api._

  override def allPersistenceIdsSource(max: Long): Source[String, NotUsed] =
    Source.fromPublisher(db.stream(queries.allPersistenceIdsDistinct(correctMaxForH2Driver(max)).result))

  override def eventsByTag(
      tag: String,
      offset: Long,
      maxOffset: Long,
      max: Long): Source[Try[(PersistentRepr, Set[String], Long)], NotUsed] = {

    val publisher = db.stream(queries.eventsByTag((s"%$tag%", offset, maxOffset, correctMaxForH2Driver(max))).result)
    // applies workaround for https://github.com/akka/akka-persistence-jdbc/issues/168
    Source
      .fromPublisher(publisher)
      .via(perfectlyMatchTag(tag, readJournalConfig.pluginConfig.tagSeparator))
      .via(serializer.deserializeFlow)
  }

  override def messages(
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long,
      max: Long): Source[Try[(PersistentRepr, Long)], NotUsed] = {
    Source
      .fromPublisher(
        db.stream(
          queries.messagesQuery((persistenceId, fromSequenceNr, toSequenceNr, correctMaxForH2Driver(max))).result))
      .via(serializer.deserializeFlow)
      .map {
        case Success((repr, _, ordering)) => Success(repr -> ordering)
        case Failure(e)                   => Failure(e)
      }
  }

  override def journalSequence(offset: Long, limit: Long): Source[Long, NotUsed] =
    Source.fromPublisher(db.stream(queries.journalSequenceQuery((offset, limit)).result))

  override def maxJournalSequence(): Future[Long] = {
    db.run(queries.maxJournalSequenceQuery.result)
  }
}

object TagFilterFlow {
  /*
   * Returns a Flow that retains every event with tags that perfectly match passed tag.
   * This is a workaround for bug https://github.com/akka/akka-persistence-jdbc/issues/168
   */
  private[dao] def perfectlyMatchTag(tag: String, separator: String) =
    Flow[JournalRow].filter(_.tags.exists(tags => tags.split(separator).contains(tag)))
}

trait OracleReadJournalDao extends ReadJournalDao {
  val db: Database
  val profile: JdbcProfile
  val readJournalConfig: ReadJournalConfig
  val queries: ReadJournalQueries
  val serializer: FlowPersistentReprSerializer[JournalRow]

  import readJournalConfig.journalTableConfiguration._
  import columnNames._

  val theTableName = schemaName.map(_ + ".").getOrElse("") + s""""$tableName""""

  import profile.api._

  private def isOracleDriver(profile: JdbcProfile): Boolean =
    profile match {
      case slick.jdbc.OracleProfile => true
      case _                        => false
    }

  abstract override def allPersistenceIdsSource(max: Long): Source[String, NotUsed] = {
    if (isOracleDriver(profile)) {
      val selectStatement =
        sql"""SELECT DISTINCT "#$persistenceId" FROM #$theTableName WHERE rownum <= $max""".as[String]
      Source.fromPublisher(db.stream(selectStatement))
    } else {
      super.allPersistenceIdsSource(max)
    }
  }

  implicit val getJournalRow = GetResult(r => JournalRow(r.<<, r.<<, r.<<, r.<<, r.nextBytes(), r.<<))

  abstract override def eventsByTag(
      tag: String,
      offset: Long,
      maxOffset: Long,
      max: Long): Source[Try[(PersistentRepr, Set[String], Long)], NotUsed] = {
    if (isOracleDriver(profile)) {
      val theOffset = Math.max(0, offset)
      val theTag = s"%$tag%"

      val selectStatement =
        sql"""
            SELECT "#$ordering", "#$deleted", "#$persistenceId", "#$sequenceNumber", "#$message", "#$tags"
            FROM (
              SELECT * FROM #$theTableName
              WHERE "#$tags" LIKE $theTag
              AND "#$ordering" > $theOffset
              AND "#$ordering" <= $maxOffset
              AND "#$deleted" = 'false'
              ORDER BY "#$ordering"
            )
            WHERE rownum <= $max""".as[JournalRow]

      // applies workaround for https://github.com/akka/akka-persistence-jdbc/issues/168
      Source
        .fromPublisher(db.stream(selectStatement))
        .via(perfectlyMatchTag(tag, readJournalConfig.pluginConfig.tagSeparator))
        .via(serializer.deserializeFlow)

    } else {
      super.eventsByTag(tag, offset, maxOffset, max)
    }
  }
}

class ByteArrayReadJournalDao(
    val db: Database,
    val profile: JdbcProfile,
    val readJournalConfig: ReadJournalConfig,
    serialization: Serialization)(implicit val ec: ExecutionContext, val mat: Materializer)
    extends BaseByteArrayReadJournalDao
    with OracleReadJournalDao {
  val queries = new ReadJournalQueries(profile, readJournalConfig)
  val serializer = new ByteArrayJournalSerializer(serialization, readJournalConfig.pluginConfig.tagSeparator)
}
