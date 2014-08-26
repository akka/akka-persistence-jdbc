package akka.persistence.jdbc.snapshot

import akka.persistence.jdbc.common.{PluginConfig, ScalikeConnection}
import akka.persistence.jdbc.util._
import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.ConfigFactory

trait JdbcSyncSnapshotStoreSpec extends SnapshotStoreSpec with ScalikeConnection with JdbcInit {
  override def cfg: PluginConfig = PluginConfig(system)
  lazy val config = ConfigFactory.load("application.conf")

  override def beforeAll() {
    dropSnapshotTable()
    createSnapshotTable()
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
  }
}

class H2JdbcSyncSnapshotStoreSpec extends JdbcSyncSnapshotStoreSpec with H2JdbcInit {
  override lazy val config = ConfigFactory.load("h2-application.conf")
}

class PostgresqlJdbcSyncSnapshotStoreSpec extends JdbcSyncSnapshotStoreSpec with PostgresqlJdbcInit {
  override lazy val config = ConfigFactory.load("postgres-application.conf")
}

class MysqlSyncSnapshotStoreSpec extends JdbcSyncSnapshotStoreSpec with MysqlJdbcInit {
  override lazy val config = ConfigFactory.load("mysql-application.conf")
}

class OracleSyncSnapshotStoreSpec extends JdbcSyncSnapshotStoreSpec with OracleJdbcInit {
  override lazy val config = ConfigFactory.load("oracle-application.conf")
}

class InformixSyncSnapshotStoreSpec extends JdbcSyncSnapshotStoreSpec with InformixJdbcInit {
  override lazy val config = ConfigFactory.load("informix-application.conf")
}