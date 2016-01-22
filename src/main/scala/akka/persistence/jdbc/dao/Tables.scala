/*
 * Copyright 2015 Dennis Vriend
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

package akka.persistence.jdbc.dao

import akka.persistence.jdbc.dao.JournalTables.{ JournalDeletedToRow, JournalRow }
import akka.persistence.jdbc.dao.SnapshotTables.SnapshotRow
import akka.persistence.jdbc.extension.{ DeletedToTableConfiguration, JournalTableConfiguration, SnapshotTableConfiguration }

object JournalTables {
  case class JournalRow(persistenceId: String, sequenceNumber: Long, message: Array[Byte], created: Long, tags: Option[String] = None)

  case class JournalDeletedToRow(persistenceId: String, deletedTo: Long)
}

trait JournalTables {
  val profile: slick.driver.JdbcProfile

  import profile.api._

  def journalTableCfg: JournalTableConfiguration

  def deletedToTableCfg: DeletedToTableConfiguration

  class Journal(_tableTag: Tag) extends Table[JournalRow](_tableTag, _schemaName = journalTableCfg.schema, _tableName = journalTableCfg.tableName) {
    def * = (persistenceId, sequenceNumber, message, created, tags) <> (JournalRow.tupled, JournalRow.unapply)

    val persistenceId: Rep[String] = column[String]("persistence_id", O.Length(255, varying = true))
    val sequenceNumber: Rep[Long] = column[Long]("sequence_number")
    val created: Rep[Long] = column[Long]("created")
    val tags: Rep[Option[String]] = column[String]("tags", O.Length(255, varying = true))
    val message: Rep[Array[Byte]] = column[Array[Byte]]("message")
    val pk = primaryKey("journal_pk", (persistenceId, sequenceNumber))
  }

  lazy val JournalTable = new TableQuery(tag ⇒ new Journal(tag))

  class DeletedTo(_tableTag: Tag) extends Table[JournalDeletedToRow](_tableTag, _schemaName = deletedToTableCfg.schema, _tableName = deletedToTableCfg.tableName) {
    def * = (persistenceId, deletedTo) <> (JournalDeletedToRow.tupled, JournalDeletedToRow.unapply)

    val persistenceId: Rep[String] = column[String]("persistence_id", O.Length(255, varying = true))
    val deletedTo: Rep[Long] = column[Long]("deleted_to")
  }

  lazy val DeletedToTable = new TableQuery(tag ⇒ new DeletedTo(tag))
}

object SnapshotTables {
  case class SnapshotRow(persistenceId: String, sequenceNumber: Long, created: Long, snapshot: Array[Byte])
}

trait SnapshotTables {
  val profile: slick.driver.JdbcProfile

  import profile.api._

  def snapshotTableCfg: SnapshotTableConfiguration

  class Snapshot(_tableTag: Tag) extends Table[SnapshotRow](_tableTag, _schemaName = snapshotTableCfg.schema, _tableName = snapshotTableCfg.tableName) {
    def * = (persistenceId, sequenceNumber, created, snapshot) <> (SnapshotRow.tupled, SnapshotRow.unapply)

    val persistenceId: Rep[String] = column[String]("persistence_id", O.Length(255, varying = true), O.PrimaryKey)
    val sequenceNumber: Rep[Long] = column[Long]("sequence_number", O.PrimaryKey)
    val created: Rep[Long] = column[Long]("created")
    val snapshot: Rep[Array[Byte]] = column[Array[Byte]]("snapshot")
    val pk = primaryKey("snapshot_pk", (persistenceId, sequenceNumber))
  }

  lazy val SnapshotTable = new TableQuery(tag ⇒ new Snapshot(tag))
}
