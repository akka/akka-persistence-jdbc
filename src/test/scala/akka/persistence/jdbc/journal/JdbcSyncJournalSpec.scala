package akka.persistence.jdbc.journal

import akka.persistence.jdbc.common.{PluginConfig, ScalikeConnection}
import akka.persistence.jdbc.util.JdbcInit
import akka.persistence.journal.LegacyJournalSpec
import com.typesafe.config.ConfigFactory

class JdbcSyncJournalSpec extends LegacyJournalSpec with ScalikeConnection with JdbcInit {
  override def pluginConfig: PluginConfig = PluginConfig(system)

  lazy val config = ConfigFactory.load("application.conf")

  override def beforeAll() {
    dropJournalTable()
    createJournalTable()
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
  }
}
