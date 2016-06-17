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

package akka.persistence.jdbc.dao.bytea

import akka.persistence.jdbc.dao.bytea.JournalTables.{ JournalDeletedToRow, JournalRow }
import akka.persistence.jdbc.config.{ DeletedToTableConfiguration, JournalTableConfiguration }
import akka.persistence.jdbc.serialization.{ SerializationResult, Serialized }
import slick.driver.JdbcProfile

class JournalQueries(val profile: JdbcProfile, override val journalTableCfg: JournalTableConfiguration, override val deletedToTableCfg: DeletedToTableConfiguration) extends JournalTables {

  import profile.api._

  def writeJournalRows(xs: Iterable[JournalRow]) =
    JournalTable ++= xs

  def insertDeletedTo(persistenceId: String, highestSequenceNr: Option[Long]) =
    DeletedToTable += JournalDeletedToRow(persistenceId, highestSequenceNr.getOrElse(0L))

  private def selectAllDeletedTo(persistenceId: Rep[String]): Query[DeletedTo, JournalDeletedToRow, Seq] =
    DeletedToTable.filter(_.persistenceId === persistenceId)

  private def selectAllJournalForPersistenceId(persistenceId: Rep[String]): Query[Journal, JournalRow, Seq] =
    JournalTable.filter(_.persistenceId === persistenceId).sortBy(_.sequenceNumber.desc)

  private def _highestSequenceNrForPersistenceId(persistenceId: Rep[String]): Rep[Option[Long]] =
    selectAllJournalForPersistenceId(persistenceId).map(_.sequenceNumber).max
  val highestSequenceNrForPersistenceId = Compiled(_highestSequenceNrForPersistenceId _)

  private def _selectByPersistenceIdAndMaxSequenceNumber(persistenceId: Rep[String], maxSequenceNr: Rep[Long]): Query[Journal, JournalRow, Seq] =
    selectAllJournalForPersistenceId(persistenceId).filter(_.sequenceNumber <= maxSequenceNr)
  val selectByPersistenceIdAndMaxSequenceNumber = Compiled(_selectByPersistenceIdAndMaxSequenceNumber _)

  private def _highestSequenceNumberFromJournalForPersistenceIdFromSequenceNr(persistenceId: Rep[String], fromSequenceNr: Rep[Long]): Rep[Option[Long]] =
    selectAllJournalForPersistenceId(persistenceId).filter(_.sequenceNumber >= fromSequenceNr).map(_.sequenceNumber).max
  val highestSequenceNumberFromJournalForPersistenceIdFromSequenceNr = Compiled(_highestSequenceNumberFromJournalForPersistenceIdFromSequenceNr _)

  private def _selectHighestSequenceNrFromDeletedTo(persistenceId: Rep[String]): Rep[Option[Long]] =
    selectAllDeletedTo(persistenceId).map(_.deletedTo).max
  val selectHighestSequenceNrFromDeletedTo = Compiled(_selectHighestSequenceNrFromDeletedTo _)

  private def _allPersistenceIdsDistinct: Query[Rep[String], String, Seq] =
    JournalTable.map(_.persistenceId).distinct
  val allPersistenceIdsDistinct = Compiled(_allPersistenceIdsDistinct)

  def journalRowByPersistenceIds(persistenceIds: Iterable[String]): Query[Rep[String], String, Seq] =
    for {
      query ← JournalTable.map(_.persistenceId)
      if query inSetBind persistenceIds
    } yield query

  private def _messagesQuery(persistenceId: Rep[String], fromSequenceNr: Rep[Long], toSequenceNr: Rep[Long], max: ConstColumn[Long]): Query[Journal, JournalRow, Seq] =
    JournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNumber >= fromSequenceNr)
      .filter(_.sequenceNumber <= toSequenceNr)
      .sortBy(_.sequenceNumber.asc)
      .take(max)
  val messagesQuery = Compiled(_messagesQuery _)
}
