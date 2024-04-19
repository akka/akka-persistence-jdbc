/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.journal.dao

import akka.persistence.jdbc.config.{ EventJournalTableConfiguration, EventTagTableConfiguration }
import akka.persistence.jdbc.journal.dao.JournalTables.{ JournalAkkaSerializationRow, TagRow }
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

class JournalQueries(
    val profile: JdbcProfile,
    override val journalTableCfg: EventJournalTableConfiguration,
    override val tagTableCfg: EventTagTableConfiguration)
    extends JournalTables {

  import profile.api._

  private val JournalTableC = Compiled(JournalTable)
  private val insertAndReturn = JournalTable.returning(JournalTable.map(_.ordering))
  private val TagTableC = Compiled(TagTable)

  val highestSequenceNrForPersistenceId = Compiled(_highestSequenceNrForPersistenceId _)
  val highestSequenceNrForPersistenceIdBefore = Compiled(_highestSequenceNrForPersistenceIdBefore _)
  val messagesQuery = Compiled(_messagesQuery _)

  def writeJournalRows(xs: Seq[(JournalAkkaSerializationRow, Set[String])])(
      implicit ec: ExecutionContext): DBIOAction[Any, NoStream, Effect.Write] = {
    val sorted = xs.sortBy(event => event._1.sequenceNumber)
    if (sorted.exists(_._2.nonEmpty)) {
      // only if there are any tags
      writeEventsAndTags(sorted)
    } else {
      // optimization avoid some work when not using tags
      val events = sorted.map(_._1)
      JournalTableC ++= events
    }
  }

  private def writeEventsAndTags(sorted: Seq[(JournalAkkaSerializationRow, Set[String])])(
      implicit ec: ExecutionContext): DBIOAction[Any, NoStream, Effect.Write] = {
    val (events, _) = sorted.unzip
    if (tagTableCfg.legacyTagKey) {
      for {
        ids <- insertAndReturn ++= events
        tagInserts = ids.zip(sorted).flatMap { case (id, (e, tags)) =>
          tags.map(tag => TagRow(Some(id), Some(e.persistenceId), Some(e.sequenceNumber), tag))
        }
        _ <- TagTableC ++= tagInserts
      } yield ()
    } else {
      val tagInserts = sorted.map { case (e, tags) =>
        tags.map(t => TagRow(None, Some(e.persistenceId), Some(e.sequenceNumber), t))
      }
      // optimization using batch insert
      for {
        _ <- JournalTableC ++= events
        _ <- TagTableC ++= tagInserts.flatten
      } yield ()
    }
  }

  private def selectAllJournalForPersistenceId(persistenceId: Rep[String]) =
    JournalTable.filter(_.persistenceId === persistenceId).sortBy(_.sequenceNumber.desc)

  def delete(persistenceId: String, toSequenceNr: Long) = {
    JournalTable.filter(_.persistenceId === persistenceId).filter(_.sequenceNumber <= toSequenceNr).delete
  }

  def markJournalMessageAsDeleted(persistenceId: String, sequenceNr: Long) =
    JournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNumber === sequenceNr)
      .filter(_.deleted === false)
      .map(_.deleted)
      .update(true)

  private def _highestSequenceNrForPersistenceId(persistenceId: Rep[String]): Rep[Option[Long]] =
    selectAllJournalForPersistenceId(persistenceId).take(1).map(_.sequenceNumber).max

  private def _highestSequenceNrForPersistenceIdBefore(
      persistenceId: Rep[String],
      maxSequenceNr: Rep[Long]): Rep[Long] =
    selectAllJournalForPersistenceId(persistenceId)
      .filter(_.sequenceNumber <= maxSequenceNr)
      .take(1)
      .map(_.sequenceNumber)
      .max
      .getOrElse(0L)

  private def _messagesQuery(
      persistenceId: Rep[String],
      fromSequenceNr: Rep[Long],
      toSequenceNr: Rep[Long],
      max: ConstColumn[Long]) =
    JournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.deleted === false)
      .filter(_.sequenceNumber >= fromSequenceNr)
      .filter(_.sequenceNumber <= toSequenceNr)
      .sortBy(_.sequenceNumber.asc)
      .take(max)

}
