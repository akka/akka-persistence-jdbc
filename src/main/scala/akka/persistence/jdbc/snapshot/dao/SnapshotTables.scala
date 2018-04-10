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

package akka.persistence.jdbc.snapshot.dao

import akka.persistence.jdbc.config.SnapshotTableConfiguration
import akka.persistence.jdbc.snapshot.dao.SnapshotTables._
import akka.persistence.jdbc.util.InputStreamOps._
import slick.jdbc.JdbcProfile

object SnapshotTables {
  case class SnapshotRow(
      persistenceId: String,
      sequenceNumber: Long,
      created: Long,
      @deprecated("This was used to store the the snapshot serialized wrapped in a Snapshot object, but no longer.", "4.0.0") snapshot: Option[Array[Byte]],
      snapshotData: Option[Array[Byte]],
      serId: Option[Int],
      serManifest: Option[String])

  def isOracleDriver(profile: JdbcProfile): Boolean = profile match {
    case slick.jdbc.OracleProfile => true
    case _                        => false
  }
}

trait SnapshotTables {
  val profile: slick.jdbc.JdbcProfile

  import profile.api._

  def snapshotTableCfg: SnapshotTableConfiguration

  class Snapshot(_tableTag: Tag) extends Table[SnapshotRow](_tableTag, _schemaName = snapshotTableCfg.schemaName, _tableName = snapshotTableCfg.tableName) {
    def * = if (snapshotTableCfg.hasSnapshotColumn) {
      (persistenceId, sequenceNumber, created, snapshot, snapshotData, serId, serManifest) <> (SnapshotRow.tupled, SnapshotRow.unapply)
    } else {
      (persistenceId, sequenceNumber, created, snapshotData, serId, serManifest) <> (
        {
          case (persistenceId, sequenceNumber, created, snapshotData, serId, serManifest) =>
            SnapshotRow(persistenceId, sequenceNumber, created, None, snapshotData, serId, serManifest)
        },
        { (row: SnapshotRow) =>
          Some((row.persistenceId, row.sequenceNumber, row.created, row.snapshotData, row.serId, row.serManifest))
        })
    }

    val persistenceId: Rep[String] = column[String](snapshotTableCfg.columnNames.persistenceId, O.Length(255, varying = true))
    val sequenceNumber: Rep[Long] = column[Long](snapshotTableCfg.columnNames.sequenceNumber)
    val created: Rep[Long] = column[Long](snapshotTableCfg.columnNames.created)
    @deprecated("This was used to store the the snapshot serialized wrapped in a Snapshot object, but no longer.", "4.0.0")
    val snapshot: Rep[Option[Array[Byte]]] = column[Option[Array[Byte]]](snapshotTableCfg.columnNames.snapshot)
    val snapshotData: Rep[Option[Array[Byte]]] = column[Option[Array[Byte]]](snapshotTableCfg.columnNames.snapshotData)
    val serId: Rep[Option[Int]] = column[Option[Int]](snapshotTableCfg.columnNames.serId)
    val serManifest: Rep[Option[String]] = column[Option[String]](snapshotTableCfg.columnNames.serManifest, O.Length(255, varying = true))
    val pk = primaryKey("snapshot_pk", (persistenceId, sequenceNumber))
  }

  case class OracleSnapshot(_tableTag: Tag) extends Snapshot(_tableTag) {

    import java.sql.Blob
    import javax.sql.rowset.serial.SerialBlob

    private val columnType = MappedColumnType.base[Array[Byte], Blob](
      bytes => new SerialBlob(bytes),
      blob => blob.getBinaryStream.toArray).optionType
    override val snapshot: Rep[Option[Array[Byte]]] = column[Option[Array[Byte]]](snapshotTableCfg.columnNames.snapshot)(columnType)
    override val snapshotData: Rep[Option[Array[Byte]]] = column[Option[Array[Byte]]](snapshotTableCfg.columnNames.snapshotData)(columnType)
  }

  lazy val SnapshotTable = new TableQuery(tag => if (isOracleDriver(profile)) OracleSnapshot(tag) else new Snapshot(tag))
}