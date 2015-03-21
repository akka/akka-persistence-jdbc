package akka.persistence.jdbc.snapshot

import akka.persistence.jdbc.common.PluginConfig
import akka.persistence.jdbc.serialization.{SnapshotSerializer, SnapshotTypeConverter}
import akka.persistence.serialization.Snapshot
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.serialization.Serialization
import scalikejdbc._

import scala.concurrent.ExecutionContext
import scala.util.Try

trait JdbcStatements {
  def deleteSnapshot(metadata: SnapshotMetadata): Unit

  def writeSnapshot(metadata: SnapshotMetadata, snapshot: Snapshot): Unit

  def selectSnapshotsFor(persistenceId: String, criteria: SnapshotSelectionCriteria): List[SelectedSnapshot]
}

trait GenericStatements extends JdbcStatements with SnapshotSerializer {
  implicit val executionContext: ExecutionContext
  implicit val session: DBSession
  val cfg: PluginConfig

  val schema = cfg.snapshotSchemaName
  val table = cfg.snapshotTableName

  implicit def snapshotConverter: SnapshotTypeConverter

  implicit def serialization: Serialization

  def deleteSnapshot(metadata: SnapshotMetadata): Unit =
    SQL(s"DELETE FROM $schema$table WHERE persistence_id = ? AND sequence_nr = ?")
      .bind(metadata.persistenceId, metadata.sequenceNr).update().apply

  def writeSnapshot(metadata: SnapshotMetadata, snapshot: Snapshot): Unit = {
    import metadata._
    Try {
      SQL(s"INSERT INTO $schema$table (persistence_id, sequence_nr, created, snapshot) VALUES (?, ?, ?, ?)")
        .bind(persistenceId, sequenceNr, timestamp, marshal(snapshot)).update().apply
    } recover {
      case ex: Exception => SQL(s"UPDATE $schema$table SET snapshot = ?, created = ? WHERE persistence_id = ? AND sequence_nr = ?")
        .bind(marshal(snapshot), timestamp, persistenceId, sequenceNr).update().apply
    }
  }

  def selectSnapshotsFor(persistenceId: String, criteria: SnapshotSelectionCriteria): List[SelectedSnapshot] =
    SQL(s"SELECT * FROM $schema$table WHERE persistence_id = ? AND sequence_nr <= ? ORDER BY sequence_nr DESC")
      .bind(persistenceId, criteria.maxSequenceNr)
      .map { rs => SelectedSnapshot(SnapshotMetadata(rs.string("persistence_id"), rs.long("sequence_nr"), rs.long("created")), unmarshal(rs.string("snapshot")).data) }
      .list()
      .apply()
      .filterNot(snap => snap.metadata.timestamp > criteria.maxTimestamp)
}

trait PostgresqlStatements extends GenericStatements

trait MySqlStatements extends GenericStatements

trait H2Statements extends GenericStatements


trait OracleStatements extends GenericStatements {
  override def writeSnapshot(metadata: SnapshotMetadata, snapshot: Snapshot): Unit = {
    import metadata._
    SQL("call sp_save_snapshot(?, ?, ?, ?)")
      .bind(persistenceId, sequenceNr, marshal(snapshot), timestamp)
      .execute()
      .apply()
  }
}

trait MSSqlServerStatements extends GenericStatements

trait DB2Statements extends GenericStatements

trait InformixStatements extends GenericStatements
