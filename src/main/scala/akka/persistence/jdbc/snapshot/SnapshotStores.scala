package akka.persistence.jdbc.snapshot

import akka.persistence.jdbc.extension.ScalikeExtension
import akka.serialization.SerializationExtension
import scalikejdbc.DBSession

trait GenericSyncSnapshotStore extends JdbcSyncSnapshotStore with GenericStatements {
  override implicit val session: DBSession = ScalikeExtension(system).session

  implicit val snapshotConverter = ScalikeExtension(context.system).snapshotConverter

  implicit val serialization = SerializationExtension(context.system)
}

class PostgresqlSyncSnapshotStore extends GenericSyncSnapshotStore with PostgresqlStatements

class MysqlSyncSnapshotStore extends GenericSyncSnapshotStore with MySqlStatements

class H2SyncSnapshotStore extends GenericSyncSnapshotStore with H2Statements

class OracleSyncSnapshotStore extends GenericSyncSnapshotStore with OracleStatements

class MSSqlServerSyncSnapshotStore extends GenericSyncSnapshotStore with MSSqlServerStatements

class InformixSyncSnapshotStore extends GenericSyncSnapshotStore with InformixStatements