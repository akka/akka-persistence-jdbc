/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.jdbc
package query.dao

import akka.NotUsed
import akka.persistence.PersistentRepr
import akka.persistence.jdbc.config.ReadJournalConfig
import akka.persistence.jdbc.journal.dao.ByteArrayJournalSerializer
import akka.persistence.jdbc.serialization.FlowPersistentReprSerializer
import akka.serialization.Serialization
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import slick.jdbc.JdbcProfile
import slick.jdbc.GetResult
import slick.jdbc.JdbcBackend._

import scala.collection.immutable._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

trait BaseByteArrayReadJournalDao extends ReadJournalDao {
  def db: Database
  val profile: JdbcProfile
  def queries: ReadJournalQueries
  def serializer: FlowPersistentReprSerializer[JournalRow]
  def readJournalConfig: ReadJournalConfig

  import profile.api._

  override def allPersistenceIdsSource(max: Long): Source[String, NotUsed] =
    Source.fromPublisher(db.stream(queries.allPersistenceIdsDistinct(max).result))

  override def eventsByTag(tag: String, offset: Long, maxOffset: Long, max: Long): Source[Try[(PersistentRepr, Set[String], Long)], NotUsed] =
    Source.fromPublisher(db.stream(queries.eventsByTag(tag, offset, maxOffset, max).result))
      .via(serializer.deserializeFlow)

  override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[Try[PersistentRepr], NotUsed] =
    Source.fromPublisher(db.stream(queries.messagesQuery(persistenceId, fromSequenceNr, toSequenceNr, max).result))
      .via(serializer.deserializeFlowWithoutTags)

  override def journalSequence(offset: Long, limit: Long): Source[Long, NotUsed] =
    Source.fromPublisher(db.stream(queries.journalSequenceQuery(offset, limit).result))

  override def maxJournalSequence(): Future[Long] = {
    db.run(queries.maxJournalSequenceQuery.result)
  }
}

trait OracleReadJournalDao extends ReadJournalDao {
  val db: Database
  val profile: JdbcProfile
  val readJournalConfig: ReadJournalConfig
  val queries: ReadJournalQueries
  val serializer: FlowPersistentReprSerializer[JournalRow]

  import readJournalConfig.journalTableConfiguration.{columnNames => journalColumnNames}
  import readJournalConfig.journalTagTableConfiguration.{columnNames => tagColumnNames}

  private def fullTableName(schemaName: Option[String], tableName: String) = schemaName.map(_ + ".").getOrElse("") + s""""$tableName""""

  val journalTableName =  fullTableName(readJournalConfig.journalTableConfiguration.schemaName, readJournalConfig.journalTableConfiguration.tableName)
  val journalTagTableName = fullTableName(readJournalConfig.journalTagTableConfiguration.schemaName, readJournalConfig.journalTagTableConfiguration.tableName)

  import profile.api._

  private def isOracleDriver(profile: JdbcProfile): Boolean = profile match {
    case slick.jdbc.OracleProfile => true
    case _                        => false
  }

  abstract override def allPersistenceIdsSource(max: Long): Source[String, NotUsed] = {
    if (isOracleDriver(profile)) {
      Source.fromPublisher(
        db.stream(sql"""SELECT DISTINCT "#${journalColumnNames.persistenceId}" FROM #$journalTableName WHERE rownum <= $max""".as[String]))
    } else {
      super.allPersistenceIdsSource(max)
    }
  }

  implicit val getJournalRow = GetResult(r => JournalRow(r.<<, r.<<, r.<<, r.<<, r.nextBytes()))

  abstract override def eventsByTag(tag: String, offset: Long, maxOffset: Long, max: Long): Source[Try[(PersistentRepr, Set[String], Long)], NotUsed] = {
    if (isOracleDriver(profile)) {
      val theOffset = Math.max(0, offset)
      Source.fromPublisher {
        db.stream(
          sql"""
            SELECT "#${journalColumnNames.ordering}", "#${journalColumnNames.deleted}", "#${journalColumnNames.persistenceId}", "#${journalColumnNames.sequenceNumber}", "#${journalColumnNames.message}"
            FROM (
              SELECT j."#${journalColumnNames.ordering}", j."#${journalColumnNames.deleted}", j."#${journalColumnNames.persistenceId}", j."#${journalColumnNames.sequenceNumber}", j."#${journalColumnNames.message}"
              FROM #$journalTableName j, #$journalTagTableName jt
              WHERE jt."#${tagColumnNames.tag}" = $tag
              AND j."#${journalColumnNames.persistenceId}" = jt."#${tagColumnNames.persistenceId}"
              AND j."#${journalColumnNames.sequenceNumber}" = jt."#${tagColumnNames.sequenceNumber}"
              AND j."#${journalColumnNames.ordering}" > $theOffset
              AND j."#${journalColumnNames.ordering}" <= $maxOffset
              ORDER BY j."#${journalColumnNames.ordering}"
            )
            WHERE rownum <= $max""".as[JournalRow])
      }.via(serializer.deserializeFlow)
    } else {
      super.eventsByTag(tag, offset, maxOffset, max)
    }
  }
}
trait H2ReadJournalDao extends ReadJournalDao {
  val profile: JdbcProfile

  private def isH2Driver(profile: JdbcProfile): Boolean = profile match {
    case slick.jdbc.H2Profile => true
    case _                    => false
  }

  abstract override def allPersistenceIdsSource(max: Long): Source[String, NotUsed] =
    super.allPersistenceIdsSource(correctMaxForH2Driver(max))

  abstract override def eventsByTag(tag: String, offset: Long, maxOffset: Long, max: Long): Source[Try[(PersistentRepr, Set[String], Long)], NotUsed] =
    super.eventsByTag(tag, offset, maxOffset, correctMaxForH2Driver(max))

  abstract override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[Try[PersistentRepr], NotUsed] =
    super.messages(persistenceId, fromSequenceNr, toSequenceNr, correctMaxForH2Driver(max))

  private def correctMaxForH2Driver(max: Long): Long = {
    if (isH2Driver(profile)) {
      Math.min(max, Int.MaxValue) // H2 only accepts a LIMIT clause as an Integer
    } else {
      max
    }
  }
}

class ByteArrayReadJournalDao(val db: Database, val profile: JdbcProfile, val readJournalConfig: ReadJournalConfig, serialization: Serialization)(implicit ec: ExecutionContext, mat: Materializer) extends BaseByteArrayReadJournalDao with OracleReadJournalDao with H2ReadJournalDao {
  val queries = new ReadJournalQueries(profile, readJournalConfig.journalTableConfiguration, readJournalConfig.journalTagTableConfiguration)
  val serializer = new ByteArrayJournalSerializer(serialization, readJournalConfig.pluginConfig.tagSeparator)
}
