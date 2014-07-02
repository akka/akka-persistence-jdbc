package akka.persistence.jdbc.snapshot

import akka.persistence.jdbc.common.{PluginConfig, ScalikeConnection}
import akka.persistence.jdbc.util.{H2JdbcInit, PostgresqlJdbcInit, JdbcInit, GenericJdbcInit}
import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.ConfigFactory

abstract class JdbcSyncSnapshotStoreSpec extends SnapshotStoreSpec with ScalikeConnection with JdbcInit {

  override def pluginConfig: PluginConfig = PluginConfig(system)

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

class PostgresqlJdbcSyncSnapshotStoreSpec extends JdbcSyncSnapshotStoreSpec with PostgresqlJdbcInit

class H2JdbcSyncSnapshotStoreSpec extends JdbcSyncSnapshotStoreSpec with H2JdbcInit
