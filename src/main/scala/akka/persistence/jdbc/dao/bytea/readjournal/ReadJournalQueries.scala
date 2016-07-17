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

package akka.persistence.jdbc.dao.bytea.readjournal

import akka.persistence.jdbc.config.JournalTableConfiguration
import akka.persistence.jdbc.dao.bytea.journal.JournalTables
import akka.persistence.jdbc.dao.bytea.journal.JournalTables.JournalRow
import slick.driver.JdbcProfile

class ReadJournalQueries(val profile: JdbcProfile, override val journalTableCfg: JournalTableConfiguration) extends JournalTables {
  import profile.api._

  def journalRowByPersistenceIds(persistenceIds: Iterable[String]): Query[Rep[String], String, Seq] =
    for {
      query <- JournalTable.map(_.persistenceId)
      if query inSetBind persistenceIds
    } yield query

  private def _allPersistenceIdsDistinct(max: ConstColumn[Long]): Query[Rep[String], String, Seq] =
    JournalTable.map(_.persistenceId).distinct.take(max)
  val allPersistenceIdsDistinct = Compiled(_allPersistenceIdsDistinct _)

  private def _messagesQuery(persistenceId: Rep[String], fromSequenceNr: Rep[Long], toSequenceNr: Rep[Long], max: ConstColumn[Long]): Query[Journal, JournalRow, Seq] =
    JournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNumber >= fromSequenceNr)
      .filter(_.sequenceNumber <= toSequenceNr)
      .sortBy(_.sequenceNumber.asc)
      .take(max)
  val messagesQuery = Compiled(_messagesQuery _)

  private def _eventsByTag(tag: Rep[String], offset: ConstColumn[Long], max: ConstColumn[Long]): Query[Journal, JournalRow, Seq] =
    JournalTable
      .filter(_.tags like tag)
      .sortBy(_.ordering.asc)
      .filter(_.ordering >= offset)
      .take(max)
  val eventsByTag = Compiled(_eventsByTag _)
}
