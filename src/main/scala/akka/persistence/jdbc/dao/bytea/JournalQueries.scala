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
import akka.persistence.jdbc.extension.{ DeletedToTableConfiguration, JournalTableConfiguration }
import akka.persistence.jdbc.serialization.{ SerializationResult, Serialized }
import slick.driver.JdbcProfile

class JournalQueries(val profile: JdbcProfile, override val journalTableCfg: JournalTableConfiguration, override val deletedToTableCfg: DeletedToTableConfiguration) extends JournalTables {

  import profile.api._

  protected final val CollectSerializedPF: PartialFunction[SerializationResult, Serialized] = {
    case e: Serialized ⇒ e
  }

  def writeList(xs: Iterable[SerializationResult]) =
    JournalTable ++= xs.collect(CollectSerializedPF)
      .map(serialized ⇒ JournalRow(serialized.persistenceId, serialized.sequenceNr, serialized.serialized, serialized.created, serialized.tags))

  def insertDeletedTo(persistenceId: String, highestSequenceNr: Option[Long]) =
    DeletedToTable += JournalDeletedToRow(persistenceId, highestSequenceNr.getOrElse(0L))

  def selectAllDeletedTo(persistenceId: String): Query[DeletedTo, JournalDeletedToRow, Seq] =
    DeletedToTable.filter(_.persistenceId === persistenceId)

  def selectAllJournalForPersistenceId(persistenceId: String): Query[Journal, JournalRow, Seq] =
    JournalTable.filter(_.persistenceId === persistenceId).sortBy(_.sequenceNumber.desc)

  def highestSequenceNrForPersistenceId(persistenceId: String): Rep[Option[Long]] =
    selectAllJournalForPersistenceId(persistenceId).map(_.sequenceNumber).max

  def selectByPersistenceIdAndMaxSequenceNumber(persistenceId: String, maxSequenceNr: Long): Query[Journal, JournalRow, Seq] =
    selectAllJournalForPersistenceId(persistenceId).filter(_.sequenceNumber <= maxSequenceNr)

  def highestSequenceNumberFromJournalForPersistenceIdFromSequenceNr(persistenceId: String, fromSequenceNr: Long): Rep[Option[Long]] =
    selectAllJournalForPersistenceId(persistenceId).filter(_.sequenceNumber >= fromSequenceNr).map(_.sequenceNumber).max

  def selectHighestSequenceNrFromDeletedTo(persistenceId: String): Rep[Option[Long]] =
    selectAllDeletedTo(persistenceId).map(_.deletedTo).max

  def allPersistenceIdsDistinct: Query[Rep[String], String, Seq] =
    JournalTable.map(_.persistenceId).distinct

  def journalRowByPersistenceIds(persistenceIds: Iterable[String]): Query[Rep[String], String, Seq] =
    for {
      query ← JournalTable.map(_.persistenceId)
      if query inSetBind persistenceIds
    } yield query

  def messagesQuery(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Query[Journal, JournalRow, Seq] =
    JournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNumber >= fromSequenceNr)
      .filter(_.sequenceNumber <= toSequenceNr)
      .sortBy(_.sequenceNumber.asc)
      .take(max)

  def eventsByTag(tag: String, offset: Long): Query[Journal, JournalRow, Seq] =
    JournalTable.filter(_.tags like s"%$tag%").sortBy(_.created.asc).drop(offset)

  def eventsByTagAndPersistenceId(persistenceId: String, tag: String, offset: Long): Query[Journal, JournalRow, Seq] =
    JournalTable.filter(_.persistenceId === persistenceId).filter(_.tags like s"%$tag%").sortBy(_.sequenceNumber.asc).drop(offset)

  def countJournal: Rep[Int] =
    JournalTable.length
}
