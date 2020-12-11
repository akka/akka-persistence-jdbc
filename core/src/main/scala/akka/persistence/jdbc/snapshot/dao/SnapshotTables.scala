package akka.persistence.jdbc.snapshot.dao

import akka.persistence.jdbc.config.SnapshotTableConfiguration
import akka.persistence.jdbc.snapshot.dao.SnapshotTables.SnapshotRow

object SnapshotTables {
  case class SnapshotRow(
      persistenceId: String,
      sequenceNumber: Long,
      created: Long,
      snapshotSerId: Int,
      snapshotSerManifest: String,
      snapshotPayload: Array[Byte],
      metaSerId: Option[Int],
      metaSerManifest: Option[String],
      metaPayload: Option[Array[Byte]])
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
    def * =
      (
        persistenceId,
        sequenceNumber,
        created,
        snapshotSerId,
        snapshotSerManifest,
        snapshotPayload,
        metaSerId,
        metaSerManifest,
        metaPayload) <> (SnapshotRow.tupled, SnapshotRow.unapply)

    val persistenceId: Rep[String] =
      column[String]("persistence_id", O.Length(255, varying = true))
    val sequenceNumber: Rep[Long] = column[Long]("sequence_number")
    val created: Rep[Long] = column[Long]("created")

    val snapshotPayload: Rep[Array[Byte]] = column[Array[Byte]]("snapshot_payload")
    val snapshotSerId: Rep[Int] = column[Int]("snapshot_ser_id")
    val snapshotSerManifest: Rep[String] = column[String]("snapshot_ser_manifest")

    val metaPayload: Rep[Option[Array[Byte]]] = column[Option[Array[Byte]]]("meta_payload")
    val metaSerId: Rep[Option[Int]] = column[Option[Int]]("meta_ser_id")
    val metaSerManifest: Rep[Option[String]] = column[Option[String]]("meta_ser_manifest")

    val pk = primaryKey(s"${tableName}_pk", (persistenceId, sequenceNumber))
  }

  // FIXME test/deal with oracle differences
  lazy val SnapshotTable = new TableQuery(tag => new Snapshot(tag))
}
