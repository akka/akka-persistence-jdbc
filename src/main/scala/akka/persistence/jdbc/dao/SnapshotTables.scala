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

package akka.persistence.jdbc.dao

import akka.persistence.jdbc.dao.SnapshotTables.SnapshotRow
import akka.persistence.jdbc.extension.SnapshotTableConfiguration

object SnapshotTables {
  case class SnapshotRow(persistenceId: String, sequenceNumber: Long, created: Long, snapshot: Array[Byte])
}

trait SnapshotTables {
  val profile: slick.driver.JdbcProfile

  import profile.api._

  def snapshotTableCfg: SnapshotTableConfiguration

  class Snapshot(_tableTag: Tag) extends Table[SnapshotRow](_tableTag, _schemaName = None, _tableName = snapshotTableCfg.tableName) {
    def * = (persistenceId, sequenceNumber, created, snapshot) <> (SnapshotRow.tupled, SnapshotRow.unapply)

    val persistenceId: Rep[String] = column[String]("persistence_id", O.Length(255, varying = true), O.PrimaryKey)
    val sequenceNumber: Rep[Long] = column[Long]("sequence_number", O.PrimaryKey)
    val created: Rep[Long] = column[Long]("created")
    val snapshot: Rep[Array[Byte]] = column[Array[Byte]]("snapshot")
    val pk = primaryKey("snapshot_pk", (persistenceId, sequenceNumber))
  }

  lazy val SnapshotTable = new TableQuery(tag ⇒ new Snapshot(tag))
}
