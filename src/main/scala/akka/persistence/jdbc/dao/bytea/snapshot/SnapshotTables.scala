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

package akka.persistence.jdbc.dao.bytea.snapshot

import akka.persistence.jdbc.config.SnapshotTableConfiguration
import akka.persistence.jdbc.util.InputStreamOps._
import slick.jdbc.JdbcProfile

object SnapshotTables {
  case class SnapshotRow(persistenceId: String, sequenceNumber: Long, created: Long, snapshot: Array[Byte])

  def isOracleDriver(profile: JdbcProfile) =
    profile.getClass.getName.contains("OracleDriver")
}

trait SnapshotTables {
  import SnapshotTables._
  val profile: slick.jdbc.JdbcProfile

  import profile.api._

  def snapshotTableCfg: SnapshotTableConfiguration

  class Snapshot(_tableTag: Tag) extends Table[SnapshotRow](_tableTag, _schemaName = snapshotTableCfg.schemaName, _tableName = snapshotTableCfg.tableName) {
    def * = (persistenceId, sequenceNumber, created, snapshot) <> (SnapshotRow.tupled, SnapshotRow.unapply)

    val persistenceId: Rep[String] = column[String](snapshotTableCfg.columnNames.persistenceId, O.Length(255, varying = true), O.PrimaryKey)
    val sequenceNumber: Rep[Long] = column[Long](snapshotTableCfg.columnNames.sequenceNumber, O.PrimaryKey)
    val created: Rep[Long] = column[Long](snapshotTableCfg.columnNames.created)
    val snapshot: Rep[Array[Byte]] = column[Array[Byte]](snapshotTableCfg.columnNames.snapshot)
    val pk = primaryKey("snapshot_pk", (persistenceId, sequenceNumber))
  }

  case class OracleSnapshot(_tableTag: Tag) extends Snapshot(_tableTag) {
    import java.sql.Blob
    import javax.sql.rowset.serial.SerialBlob

    private val columnType = MappedColumnType.base[Array[Byte], Blob](
      bytes => new SerialBlob(bytes),
      blob => blob.getBinaryStream.toArray
    )
    override val snapshot: Rep[Array[Byte]] = column[Array[Byte]](snapshotTableCfg.columnNames.snapshot)(columnType)
  }
  lazy val SnapshotTable = new TableQuery(tag => if (isOracleDriver(profile)) OracleSnapshot(tag) else new Snapshot(tag))
}