package akka.persistence.jdbc.snapshot

import akka.persistence.jdbc.common.PluginConfig
import akka.persistence.jdbc.util.{Base64, EncodeDecode}
import akka.persistence.serialization.Snapshot
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import scalikejdbc._

import scala.concurrent.ExecutionContext
import scala.util.Try

trait JdbcStatements {
  def deleteSnapshot(metadata: SnapshotMetadata): Unit

  def writeSnapshot(metadata: SnapshotMetadata, snapshot: Snapshot): Unit

  def selectSnapshotsFor(persistenceId: String, criteria: SnapshotSelectionCriteria): List[SelectedSnapshot]
}

trait GenericStatements extends JdbcStatements with EncodeDecode {
  implicit val executionContext: ExecutionContext
  implicit val session: DBSession
  val cfg: PluginConfig

  implicit val base64 = cfg.base64
  val schema = cfg.snapshotSchemaName
  val table = cfg.snapshotTableName

  def deleteSnapshot(metadata: SnapshotMetadata): Unit =
    SQL(s"DELETE FROM $schema$table WHERE persistence_id = ? AND sequence_nr = ?")
      .bind(metadata.persistenceId, metadata.sequenceNr).update().apply

  def writeSnapshot(metadata: SnapshotMetadata, snapshot: Snapshot): Unit = {
    val snapshotData = encodeString(Snapshot.toBytes(snapshot))
    import metadata._
    Try {
      SQL(s"INSERT INTO $schema$table (persistence_id, sequence_nr, created, snapshot) VALUES (?, ?, ?, ?)")
        .bind(persistenceId, sequenceNr, timestamp, snapshotData).update().apply
    } recover {
      case ex: Exception => SQL(s"UPDATE $schema$table SET snapshot = ?, created = ? WHERE persistence_id = ? AND sequence_nr = ?")
        .bind(snapshotData, timestamp, persistenceId, sequenceNr).update().apply
    }
  }

  def selectSnapshotsFor(persistenceId: String, criteria: SnapshotSelectionCriteria): List[SelectedSnapshot] =
    SQL(s"SELECT * FROM $schema$table WHERE persistence_id = ? AND sequence_nr <= ? ORDER BY sequence_nr DESC")
      .bind(persistenceId, criteria.maxSequenceNr)
      .map { rs =>
      SelectedSnapshot(SnapshotMetadata(rs.string("persistence_id"), rs.long("sequence_nr"), rs.long("created")),
        Snapshot.fromBytes(decodeBinary(rs.string("snapshot"))).data)
    }
      .list()
      .apply()
      .filterNot(snap => snap.metadata.timestamp > criteria.maxTimestamp)
}

trait PostgresqlStatements extends GenericStatements

trait MySqlStatements extends GenericStatements

trait H2Statements extends GenericStatements

trait OracleStatements extends GenericStatements {
  override def writeSnapshot(metadata: SnapshotMetadata, snapshot: Snapshot): Unit = {
    val snapshotData = encodeString(Snapshot.toBytes(snapshot))
    import metadata._

    SQL( s"""MERGE INTO $schema$table snapshot
              USING (SELECT {persistenceId} AS persistence_id, {sequenceNr} AS seq_nr from DUAL) val
              ON (snapshot.persistence_id = val.persistence_id and snapshot.sequence_nr = val.seq_nr)
              WHEN MATCHED THEN
                UPDATE SET snapshot={snap}
              WHEN NOT MATCHED THEN
                INSERT (PERSISTENCE_ID, SEQUENCE_NR, SNAPSHOT, CREATED) VALUES ({persistenceId}, {sequenceNr}, {snap}, {created})""")
      .bindByName('persistenceId -> persistenceId, 'sequenceNr -> sequenceNr, 'created -> timestamp, 'snap -> snapshotData).execute().apply
  }
}

trait MSSqlServerStatements extends GenericStatements

trait DB2Statements extends GenericStatements

trait InformixStatements extends GenericStatements
