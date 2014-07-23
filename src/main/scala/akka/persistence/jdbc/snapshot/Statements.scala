package akka.persistence.jdbc.snapshot

import akka.persistence.{SelectedSnapshot, SnapshotSelectionCriteria, SnapshotMetadata}
import akka.persistence.jdbc.common.ScalikeConnection
import akka.persistence.jdbc.util.{EncodeDecode, Base64}
import akka.persistence.serialization.Snapshot
import scalikejdbc._

import scala.concurrent.ExecutionContext

trait JdbcStatements {
  def deleteSnapshot(metadata: SnapshotMetadata)

  def writeSnapshot(metadata: SnapshotMetadata, snapshot: Snapshot)

  def selectSnapshotsFor(persistenceId: String, criteria: SnapshotSelectionCriteria): List[SelectedSnapshot]
}

trait GenericStatements extends JdbcStatements with ScalikeConnection with EncodeDecode {
  implicit def executionContext: ExecutionContext

  def deleteSnapshot(metadata: SnapshotMetadata): Unit =
    sql"DELETE FROM snapshot WHERE persistence_id = ${metadata.persistenceId} AND sequence_nr = ${metadata.sequenceNr}".update.apply

  def writeSnapshot(metadata: SnapshotMetadata, snapshot: Snapshot): Unit = {
    val snapshotData = Base64.encodeString(Snapshot.toBytes(snapshot))
    sql"INSERT INTO snapshot (persistence_id, sequence_nr, created, snapshot) VALUES (${metadata.persistenceId}, ${metadata.sequenceNr}, ${metadata.timestamp} , ${snapshotData})".update.apply
  }

  def selectSnapshotsFor(persistenceId: String, criteria: SnapshotSelectionCriteria): List[SelectedSnapshot] =
    sql"SELECT * FROM snapshot WHERE persistence_id = ${persistenceId} AND sequence_nr <= ${criteria.maxSequenceNr} ORDER BY sequence_nr DESC"
      .map { rs =>
        SelectedSnapshot(SnapshotMetadata(rs.string("persistence_id"), rs.long("sequence_nr"), rs.long("created")),
        Snapshot.fromBytes(Base64.decodeBinary(rs.string("snapshot"))).data)
      }
      .list()
      .apply()
      .filterNot(snap => snap.metadata.timestamp > criteria.maxTimestamp)
}

trait PostgresqlStatements extends GenericStatements

trait MySqlStatements extends GenericStatements

trait H2Statements extends GenericStatements

trait OracleStatements extends GenericStatements

trait MSSqlServerStatements extends GenericStatements

trait DB2Statements extends GenericStatements

trait InformixStatements extends GenericStatements