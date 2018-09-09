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
package journal.dao

import akka.persistence.jdbc.config.JournalTableConfiguration

import slick.jdbc.JdbcProfile
import slick.sql.FixedSqlAction

trait GenericQueries {
  import slick.dbio.Effect
  val profile: JdbcProfile
  import profile.api._
  def writeJournalRows(xs: Seq[JournalRow]): FixedSqlAction[Option[Int], NoStream, Effect.Write]
  def delete(persistenceId: String, toSequenceNr: Long): FixedSqlAction[Int, NoStream, Effect.Write]
  def update(persistenceId: String, seqNr: Long, newRow: JournalRow): FixedSqlAction[Int, NoStream, Effect.Write]
  def markJournalMessagesAsDeleted(persistenceId: String, maxSequenceNr: Long): FixedSqlAction[Int, NoStream, Effect.Write]
  def journalRowByPersistenceIds(persistenceIds: Iterable[String]): Query[Rep[String], String, Seq]
}

class LegacyJournalQueries(val profile: JdbcProfile, override val journalTableCfg: JournalTableConfiguration) extends LegacyJournalTables with GenericQueries {
  import profile.api._
  private val JournalTableC = Compiled(JournalTable)
  import slick.dbio.Effect
  def writeJournalRows(xs: Seq[JournalRow]): FixedSqlAction[Option[Int], NoStream, Effect.Write] = ???
  def delete(persistenceId: String, toSequenceNr: Long): FixedSqlAction[Int, NoStream, Effect.Write] = ???
  def update(persistenceId: String, seqNr: Long, newRow: JournalRow): FixedSqlAction[Int, NoStream, Effect.Write] = ???
  def markJournalMessagesAsDeleted(persistenceId: String, maxSequenceNr: Long): FixedSqlAction[Int, NoStream, Effect.Write] = ???
  def journalRowByPersistenceIds(persistenceIds: Iterable[String]): Query[Rep[String], String, Seq] = ???

}

class JournalQueries(val profile: JdbcProfile, override val journalTableCfg: JournalTableConfiguration) extends JournalTables with GenericQueries {

  import profile.api._

  import slick.dbio.Effect

  private val JournalTableC = Compiled(JournalTable)

  def writeJournalRows(xs: Seq[JournalRow]) =
    JournalTableC ++= xs.sortBy(_.sequenceNumber)

  private def selectAllJournalForPersistenceId(persistenceId: Rep[String]) =
    JournalTable.filter(_.persistenceId === persistenceId).sortBy(_.sequenceNumber.desc)

  def delete(persistenceId: String, toSequenceNr: Long): FixedSqlAction[Int, NoStream, Effect.Write] = {
    JournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNumber <= toSequenceNr)
      .delete
  }

  /**
   * Updates (!) a payload stored in a specific events row.
   * Intended to be used sparingly, e.g. moving all events to their encrypted counterparts.
   */
  def update(persistenceId: String, seqNr: Long, newRow: JournalRow): FixedSqlAction[Int, NoStream, Effect.Write] = {
    val baseQuery = JournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNumber === seqNr)

    baseQuery.map(row => (row.event, row.serId, row.serManifest)).update((newRow.event, newRow.serId, newRow.serManifest))
  }

  def markJournalMessagesAsDeleted(persistenceId: String, maxSequenceNr: Long): FixedSqlAction[Int, NoStream, Effect.Write] =
    JournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNumber <= maxSequenceNr)
      .filter(_.deleted === false)
      .map(_.deleted).update(true)

  private def _highestSequenceNrForPersistenceId(persistenceId: Rep[String]): Query[Rep[Long], Long, Seq] =
    selectAllJournalForPersistenceId(persistenceId).map(_.sequenceNumber).take(1)

  private def _highestMarkedSequenceNrForPersistenceId(persistenceId: Rep[String]): Query[Rep[Long], Long, Seq] =
    selectAllJournalForPersistenceId(persistenceId).filter(_.deleted === true).map(_.sequenceNumber)

  val highestSequenceNrForPersistenceId = Compiled(_highestSequenceNrForPersistenceId _)

  val highestMarkedSequenceNrForPersistenceId = Compiled(_highestMarkedSequenceNrForPersistenceId _)

  private def _selectByPersistenceIdAndMaxSequenceNumber(persistenceId: Rep[String], maxSequenceNr: Rep[Long]) =
    selectAllJournalForPersistenceId(persistenceId).filter(_.sequenceNumber <= maxSequenceNr)

  val selectByPersistenceIdAndMaxSequenceNumber = Compiled(_selectByPersistenceIdAndMaxSequenceNumber _)

  private def _allPersistenceIdsDistinct: Query[Rep[String], String, Seq] =
    JournalTable.map(_.persistenceId).distinct

  val allPersistenceIdsDistinct = Compiled(_allPersistenceIdsDistinct)

  def journalRowByPersistenceIds(persistenceIds: Iterable[String]): Query[Rep[String], String, Seq] = for {
    query <- JournalTable.map(_.persistenceId)
    if query inSetBind persistenceIds
  } yield query

  private def _messagesQuery(persistenceId: Rep[String], fromSequenceNr: Rep[Long], toSequenceNr: Rep[Long], max: ConstColumn[Long]) =
    JournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.deleted === false)
      .filter(_.sequenceNumber >= fromSequenceNr)
      .filter(_.sequenceNumber <= toSequenceNr)
      .sortBy(_.sequenceNumber.asc)
      .take(max)

  val messagesQuery = Compiled(_messagesQuery _)

}
