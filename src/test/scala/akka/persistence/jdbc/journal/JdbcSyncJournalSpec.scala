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

class H2SyncJournalSpec extends JdbcSyncJournalSpec with H2JdbcInit {
  override lazy val config = ConfigFactory.load("h2-application.conf")
}

class PostgresqlSyncJournalSpec extends JdbcSyncJournalSpec with PostgresqlJdbcInit {
  override lazy val config = ConfigFactory.load("postgres-application.conf")
}

class MysqlSyncJournalSpec extends JdbcSyncJournalSpec with MysqlJdbcInit {
  override lazy val config = ConfigFactory.load("mysql-application.conf")
}

class OracleSyncJournalSpec extends JdbcSyncJournalSpec with OracleJdbcInit {
  override lazy val config = ConfigFactory.load("oracle-application.conf")
}

class InformixSyncJournalSpec extends JdbcSyncJournalSpec with InformixJdbcInit {
  override lazy val config = ConfigFactory.load("informix-application.conf")
}
