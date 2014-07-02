package akka.persistence.jdbc.snapshot

import akka.persistence.jdbc.common.{PluginConfig, ScalikeConnection}
import akka.persistence.jdbc.util.JdbcInit
import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.ConfigFactory

class JdbcSyncSnapshotStoreSpec extends SnapshotStoreSpec with ScalikeConnection with JdbcInit {

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
