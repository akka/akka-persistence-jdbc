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

import akka.persistence.jdbc.config.JournalTableConfiguration
import akka.persistence.jdbc.journal.dao.JournalTables
import slick.jdbc.JdbcProfile

class ReadJournalQueries(val profile: JdbcProfile, override val journalTableCfg: JournalTableConfiguration) extends JournalTables {
  import profile.api._

  def journalRowByPersistenceIds(persistenceIds: Iterable[String]) =
    for {
      query <- JournalTable.map(_.persistenceId)
      if query inSetBind persistenceIds
    } yield query

  private def _allPersistenceIdsDistinct(max: ConstColumn[Long]): Query[Rep[String], String, Seq] =
    JournalTable.map(_.persistenceId).distinct.take(max)
  val allPersistenceIdsDistinct = Compiled(_allPersistenceIdsDistinct _)

  private def _messagesQuery(persistenceId: Rep[String], fromSequenceNr: Rep[Long], toSequenceNr: Rep[Long], max: ConstColumn[Long]) =
    JournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNumber >= fromSequenceNr)
      .filter(_.sequenceNumber <= toSequenceNr)
      .sortBy(_.sequenceNumber.asc)
      .take(max)
  val messagesQuery = Compiled(_messagesQuery _)

  private def _eventsByTag(tag: Rep[String], offset: ConstColumn[Long], maxOffset: ConstColumn[Long], max: ConstColumn[Long]) =
    JournalTable
      .filter(_.tags like tag)
      .sortBy(_.ordering.asc)
      .filter(row => row.ordering > offset && row.ordering <= maxOffset)
      .take(max)
  val eventsByTag = Compiled(_eventsByTag _)

  def writeJournalRows(xs: Seq[JournalRow]) = JournalTable ++= xs.sortBy(_.sequenceNumber)

  private def _journalSequenceQuery(from: ConstColumn[Long], limit: ConstColumn[Long]) =
    JournalTable
      .filter(_.ordering > from)
      .map(_.ordering)
      .sorted
      .take(limit)

  val journalSequenceQuery = Compiled(_journalSequenceQuery _)

  val maxJournalSequenceQuery = Compiled {
    JournalTable.map(_.ordering).max.getOrElse(0L)
  }
}
