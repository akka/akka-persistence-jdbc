/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.snapshot.dao

import akka.persistence.jdbc.config.SnapshotTableConfiguration
import akka.persistence.jdbc.snapshot.dao.SnapshotTables._
import akka.persistence.jdbc.util.InputStreamOps._
import slick.jdbc.JdbcProfile

object SnapshotTables {
  case class SnapshotRow(persistenceId: String, sequenceNumber: Long, created: Long, snapshot: Array[Byte])
  def isOracleDriver(profile: JdbcProfile): Boolean =
    profile match {
      case slick.jdbc.OracleProfile => true
      case _                        => false
    }
}

trait SnapshotTables {
  val profile: slick.jdbc.JdbcProfile

  import profile.api._

  def snapshotTableCfg: SnapshotTableConfiguration

  class Snapshot(_tableTag: Tag)
      extends Table[SnapshotRow](
        _tableTag,
        _schemaName = snapshotTableCfg.schemaName,
        _tableName = snapshotTableCfg.tableName) {
    def * = (persistenceId, sequenceNumber, created, snapshot) <> (SnapshotRow.tupled, SnapshotRow.unapply)

    val persistenceId: Rep[String] =
      column[String](snapshotTableCfg.columnNames.persistenceId, O.Length(255, varying = true))
    val sequenceNumber: Rep[Long] = column[Long](snapshotTableCfg.columnNames.sequenceNumber)
    val created: Rep[Long] = column[Long](snapshotTableCfg.columnNames.created)
    val snapshot: Rep[Array[Byte]] = column[Array[Byte]](snapshotTableCfg.columnNames.snapshot)
    val pk = primaryKey(s"${tableName}_pk", (persistenceId, sequenceNumber))
  }

  case class OracleSnapshot(_tableTag: Tag) extends Snapshot(_tableTag) {
    import java.sql.Blob
    import javax.sql.rowset.serial.SerialBlob

    private val columnType =
      MappedColumnType.base[Array[Byte], Blob](bytes => new SerialBlob(bytes), blob => blob.getBinaryStream.toArray)
    override val snapshot: Rep[Array[Byte]] = column[Array[Byte]](snapshotTableCfg.columnNames.snapshot)(columnType)
  }

  lazy val SnapshotTable = new TableQuery(tag =>
    if (isOracleDriver(profile)) OracleSnapshot(tag) else new Snapshot(tag))
}
