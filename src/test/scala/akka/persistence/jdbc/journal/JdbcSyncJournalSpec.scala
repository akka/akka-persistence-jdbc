package akka.persistence.jdbc.journal

import akka.persistence.jdbc.common.{PluginConfig, ScalikeConnection}
import akka.persistence.jdbc.util._
import akka.persistence.journal.LegacyJournalSpec
import com.typesafe.config.ConfigFactory

abstract class JdbcSyncJournalSpec extends LegacyJournalSpec with ScalikeConnection with JdbcInit {
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

class PostgresqlSyncJournalSpec extends JdbcSyncJournalSpec with PostgresqlJdbcInit

class H2SyncJournalSpec extends JdbcSyncJournalSpec with H2JdbcInit

class MysqlSyncJournalSpec extends JdbcSyncJournalSpec with MysqlJdbcInit

class OracleSyncJournalSpec extends JdbcSyncJournalSpec with OracleJdbcInit