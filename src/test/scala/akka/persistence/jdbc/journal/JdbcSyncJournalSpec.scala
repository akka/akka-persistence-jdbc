package akka.persistence.jdbc.journal

import akka.persistence.jdbc.common.PluginConfig
import akka.persistence.jdbc.extension.ScalikeExtension
import akka.persistence.jdbc.util._
import akka.persistence.journal.LegacyJournalSpec
import com.typesafe.config.ConfigFactory
import scalikejdbc.DBSession

abstract class JdbcSyncJournalSpec extends LegacyJournalSpec with JdbcInit {
  val cfg = PluginConfig(system)
  lazy val config = ConfigFactory.load("application.conf")

  override def beforeAll() {
    dropJournalTable()
    createJournalTable()
    dropSnapshotTable()
    createSnapshotTable()
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
  }
}

trait GenericJdbcJournalSpec extends JdbcSyncJournalSpec {
  implicit val session: DBSession = ScalikeExtension(system).session
}

class H2SyncJournalSpec extends GenericJdbcJournalSpec with H2JdbcInit {
  override lazy val config = ConfigFactory.load("h2-application.conf")
}

class PostgresqlSyncJournalSpec extends GenericJdbcJournalSpec with PostgresqlJdbcInit {
  override lazy val config = ConfigFactory.load("postgres-application.conf")
}

class MysqlSyncJournalSpec extends GenericJdbcJournalSpec with MysqlJdbcInit {
  override lazy val config = ConfigFactory.load("mysql-application.conf")
}

class OracleSyncJournalSpec extends GenericJdbcJournalSpec with OracleJdbcInit {
  override lazy val config = ConfigFactory.load("oracle-application.conf")
}

//class InformixSyncJournalSpec extends GenericJdbcJournalSpec with InformixJdbcInit {
//  override lazy val config = ConfigFactory.load("informix-application.conf")
//}
