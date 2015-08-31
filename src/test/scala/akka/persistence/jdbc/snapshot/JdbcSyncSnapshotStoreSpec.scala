/*
 * Copyright 2015 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.jdbc.snapshot

import akka.event.Logging
import akka.persistence.SnapshotProtocol.{ LoadSnapshotResult, LoadSnapshot, SaveSnapshot }
import akka.persistence.jdbc.actor.MacBeth
import akka.persistence.jdbc.common.PluginConfig
import akka.persistence.jdbc.extension.ScalikeExtension
import akka.persistence.jdbc.util._
import akka.persistence.snapshot.SnapshotStoreSpec
import akka.persistence.{ SelectedSnapshot, SnapshotSelectionCriteria, SaveSnapshotSuccess, SnapshotMetadata }
import akka.testkit.TestProbe
import com.typesafe.config.{ Config, ConfigFactory }
import scalikejdbc.DBSession
import scala.concurrent.duration._

abstract class JdbcSyncSnapshotStoreSpec(config: Config) extends SnapshotStoreSpec(config) with JdbcInit {
  val cfg = PluginConfig(system)
  val log = Logging(system, this.getClass)

  "The snapshot store must also" must {
    "be able to store a snapshot when the state has not changed" in {
      val senderProbe = TestProbe()
      val metadata = SnapshotMetadata("same-pid", 1)
      snapshotStore.tell(SaveSnapshot(metadata, MacBeth.text), senderProbe.ref)
      senderProbe.expectMsgPF() { case SaveSnapshotSuccess(md) ⇒ md }
      snapshotStore.tell(SaveSnapshot(metadata, MacBeth.text), senderProbe.ref)
      senderProbe.expectMsgPF() { case SaveSnapshotSuccess(md) ⇒ md }
    }

    "be able to store a whole lot of snapshots without running out of memory when requesting latest shapshot with latest timestamp" in {
      val senderProbe = TestProbe()
      val pid = "pid-1000-get-latest"
      (1 to 1000).toStream.foreach { seqNo ⇒
        if (seqNo % 100 == 0) log.info("{}", seqNo)
        val metadata = SnapshotMetadata(persistenceId = pid, sequenceNr = seqNo, timestamp = System.currentTimeMillis())
        snapshotStore.tell(SaveSnapshot(metadata, MacBeth.text), senderProbe.ref)
        senderProbe.expectMsgPF(10.minute) {
          case SaveSnapshotSuccess(md) ⇒ md
          case notSucess: Object ⇒
            log.error(notSucess.toString)
        }
      }
      snapshotStore.tell(LoadSnapshot(pid, SnapshotSelectionCriteria.Latest, Long.MaxValue), senderProbe.ref)
      senderProbe.expectMsgPF() {
        case lssr @ LoadSnapshotResult(Some(SelectedSnapshot(SnapshotMetadata(`pid`, 1000, _), MacBeth.text)), _) ⇒ lssr
      }
    }

    "be able to store a whole lot of snapshots without running out of memory when requesting one but last snapshot" in {
      val senderProbe = TestProbe()
      val pid = "pid-1000-get-999"
      (1 to 1000).toStream.foreach { seqNo ⇒
        val metadata = SnapshotMetadata(persistenceId = pid, sequenceNr = seqNo, timestamp = System.currentTimeMillis())
        snapshotStore.tell(SaveSnapshot(metadata, MacBeth.text), senderProbe.ref)
        senderProbe.expectMsgPF(1.minute) { case SaveSnapshotSuccess(md) ⇒ md }
      }
      snapshotStore.tell(LoadSnapshot(pid, SnapshotSelectionCriteria(999, Long.MaxValue), Long.MaxValue), senderProbe.ref)
      senderProbe.expectMsgPF(3.minute) {
        case lssr @ LoadSnapshotResult(Some(SelectedSnapshot(SnapshotMetadata(`pid`, 999, _), MacBeth.text)), _) ⇒ lssr
      }
    }
  }

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

// config = ConfigFactory.load("application.conf")
abstract class GenericSyncSnapshotStoreSpec(config: Config) extends JdbcSyncSnapshotStoreSpec(config) {
  implicit val session: DBSession = ScalikeExtension(system).session
}

class H2JdbcSyncSnapshotStoreSpec extends GenericSyncSnapshotStoreSpec(config = ConfigFactory.load("h2-application.conf")) with H2JdbcInit

class PostgresqlJdbcSyncSnapshotStoreSpec extends GenericSyncSnapshotStoreSpec(config = ConfigFactory.load("postgres-application.conf")) with PostgresqlJdbcInit

class MysqlSyncSnapshotStoreSpec extends GenericSyncSnapshotStoreSpec(config = ConfigFactory.load("mysql-application.conf")) with MysqlJdbcInit

class OracleSyncSnapshotStoreSpec extends GenericSyncSnapshotStoreSpec(config = ConfigFactory.load("oracle-application.conf")) with OracleJdbcInit
