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

import akka.persistence.jdbc.config.JournalTableConfiguration

object ReadJournalTables {
  case class JournalRow(persistenceId: String, sequenceNumber: Long, message: Array[Byte], created: Long, tags: Option[String] = None)
}

trait ReadJournalTables {
  import ReadJournalTables._
  val profile: slick.driver.JdbcProfile

  import profile.api._

  def journalTableCfg: JournalTableConfiguration

  class Journal(_tableTag: Tag) extends Table[JournalRow](_tableTag, _schemaName = journalTableCfg.schemaName, _tableName = journalTableCfg.tableName) {
    def * = (persistenceId, sequenceNumber, message, created, tags) <> (JournalRow.tupled, JournalRow.unapply)

    val persistenceId: Rep[String] = column[String](journalTableCfg.columnNames.persistenceId, O.Length(255, varying = true))
    val sequenceNumber: Rep[Long] = column[Long](journalTableCfg.columnNames.sequenceNumber)
    val created: Rep[Long] = column[Long](journalTableCfg.columnNames.created)
    val tags: Rep[Option[String]] = column[String](journalTableCfg.columnNames.tags, O.Length(255, varying = true))
    val message: Rep[Array[Byte]] = column[Array[Byte]](journalTableCfg.columnNames.message)
    val pk = primaryKey("journal_pk", (persistenceId, sequenceNumber))
  }

  lazy val JournalTable = new TableQuery(tag ⇒ new Journal(tag))
}
