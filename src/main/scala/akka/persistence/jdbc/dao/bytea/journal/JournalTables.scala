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

package akka.persistence.jdbc.dao.bytea.journal

import akka.persistence.jdbc.config.JournalTableConfiguration
import akka.persistence.jdbc.dao.bytea.journal.JournalTables._

object JournalTables {
  case class JournalRow(ordering: Long, deleted: Boolean, persistenceId: String, sequenceNumber: Long, message: Array[Byte], tags: Option[String] = None)
}

trait JournalTables {
  val profile: slick.jdbc.JdbcProfile

  import profile.api._

  def journalTableCfg: JournalTableConfiguration

  class Journal(_tableTag: Tag) extends Table[JournalRow](_tableTag, _schemaName = journalTableCfg.schemaName, _tableName = journalTableCfg.tableName) {
    def * = (ordering, deleted, persistenceId, sequenceNumber, message, tags) <> (JournalRow.tupled, JournalRow.unapply)

    val ordering: Rep[Long] = column[Long](journalTableCfg.columnNames.ordering, O.AutoInc)
    val persistenceId: Rep[String] = column[String](journalTableCfg.columnNames.persistenceId, O.Length(255, varying = true))
    val sequenceNumber: Rep[Long] = column[Long](journalTableCfg.columnNames.sequenceNumber)
    val deleted: Rep[Boolean] = column[Boolean](journalTableCfg.columnNames.deleted, O.Default(false))
    val tags: Rep[Option[String]] = column[Option[String]](journalTableCfg.columnNames.tags, O.Length(255, varying = true))
    val message: Rep[Array[Byte]] = column[Array[Byte]](journalTableCfg.columnNames.message)
    val pk = primaryKey("journal_pk", (ordering, persistenceId, sequenceNumber))
  }

  lazy val JournalTable = new TableQuery(tag => new Journal(tag))
}
