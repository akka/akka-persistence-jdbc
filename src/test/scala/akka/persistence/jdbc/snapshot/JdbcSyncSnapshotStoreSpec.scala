package akka.persistence.jdbc.snapshot

import akka.persistence.jdbc.extension.ScalikeExtension
import akka.persistence.{SaveSnapshotSuccess, SnapshotMetadata}
import akka.persistence.SnapshotProtocol.SaveSnapshot
import akka.persistence.jdbc.common.PluginConfig
import akka.persistence.jdbc.util._
import akka.persistence.snapshot.SnapshotStoreSpec
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import scalikejdbc.DBSession

trait JdbcSyncSnapshotStoreSpec extends SnapshotStoreSpec with JdbcInit {
  val cfg = PluginConfig(system)
  lazy val config = ConfigFactory.load("application.conf")

  "The snapshot store must also" must {
    "be able to store a snapshot when the state has not changed" in {
      val senderProbe = TestProbe()
      val metadata = SnapshotMetadata("same-pid", 1)
      snapshotStore.tell(SaveSnapshot(metadata, "data"), senderProbe.ref)
      senderProbe.expectMsgPF() { case SaveSnapshotSuccess(md) => md }
      snapshotStore.tell(SaveSnapshot(metadata, "data"), senderProbe.ref)
      senderProbe.expectMsgPF() { case SaveSnapshotSuccess(md) => md }
    }
  }

  override def beforeAll() {
    dropSnapshotTable()
    createSnapshotTable()
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
  }
}

trait GenericSyncSnapshotStoreSpec extends JdbcSyncSnapshotStoreSpec {
  implicit val session: DBSession = ScalikeExtension(system).session
}

class H2JdbcSyncSnapshotStoreSpec extends GenericSyncSnapshotStoreSpec with H2JdbcInit {
  override lazy val config = ConfigFactory.load("h2-application.conf")
}

class PostgresqlJdbcSyncSnapshotStoreSpec extends GenericSyncSnapshotStoreSpec with PostgresqlJdbcInit {
  override lazy val config = ConfigFactory.load("postgres-application.conf")
}

class MysqlSyncSnapshotStoreSpec extends GenericSyncSnapshotStoreSpec with MysqlJdbcInit {
  override lazy val config = ConfigFactory.load("mysql-application.conf")
}

class OracleSyncSnapshotStoreSpec extends GenericSyncSnapshotStoreSpec with OracleJdbcInit {
  override lazy val config = ConfigFactory.load("oracle-application.conf")
}

class InformixSyncSnapshotStoreSpec extends GenericSyncSnapshotStoreSpec with InformixJdbcInit {
  override lazy val config = ConfigFactory.load("informix-application.conf")
}