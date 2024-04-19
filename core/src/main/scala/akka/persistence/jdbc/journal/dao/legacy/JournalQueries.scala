/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc
package journal.dao.legacy

import akka.persistence.jdbc.config.LegacyJournalTableConfiguration
import slick.jdbc.JdbcProfile

class JournalQueries(val profile: JdbcProfile, override val journalTableCfg: LegacyJournalTableConfiguration)
    extends JournalTables {
  import profile.api._

  private val JournalTableC = Compiled(JournalTable)

  val highestSequenceNrForPersistenceId = Compiled(_highestSequenceNrForPersistenceId _)
  val highestSequenceNrForPersistenceIdBefore = Compiled(_highestSequenceNrForPersistenceIdBefore _)
  val messagesQuery = Compiled(_messagesQuery _)

  def writeJournalRows(xs: Seq[JournalRow]) =
    JournalTableC ++= xs.sortBy(_.sequenceNumber)

  private def selectAllJournalForPersistenceId(persistenceId: Rep[String]) =
    JournalTable.filter(_.persistenceId === persistenceId).sortBy(_.sequenceNumber.desc)

  def delete(persistenceId: String, toSequenceNr: Long) = {
    JournalTable.filter(_.persistenceId === persistenceId).filter(_.sequenceNumber <= toSequenceNr).delete
  }

  /**
   * Updates (!) a payload stored in a specific events row.
   * Intended to be used sparingly, e.g. moving all events to their encrypted counterparts.
   */
  def update(persistenceId: String, seqNr: Long, replacement: Array[Byte]) = {
    val baseQuery = JournalTable.filter(_.persistenceId === persistenceId).filter(_.sequenceNumber === seqNr)

    baseQuery.map(_.message).update(replacement)
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
