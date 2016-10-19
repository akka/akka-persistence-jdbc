/*
 * Copyright 2016 Dennis Vriend
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

import akka.actor.{ ActorRef, ActorSystem }
import akka.persistence.SnapshotProtocol._
import akka.persistence._
import akka.persistence.jdbc.config._
import akka.persistence.jdbc.util.Schema._
import akka.persistence.jdbc.util.{ ClasspathResources, DropCreate, SlickDatabase }
import akka.persistence.snapshot.SnapshotStoreSpec
import akka.testkit.TestProbe
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._

class BigBlobSnapshotStoreSpec(config: Config, schemaType: SchemaType) extends PluginSpec(config) with BeforeAndAfterAll with ScalaFutures with ClasspathResources with DropCreate {

  implicit lazy val system = ActorSystem("SnapshotStoreSpec", config.withFallback(SnapshotStoreSpec.config))

  implicit val pc: PatienceConfig = PatienceConfig(timeout = 10.seconds)

  implicit val ec = system.dispatcher

  val cfg = system.settings.config.getConfig("jdbc-journal")

  val journalConfig = new JournalConfig(cfg)

  val db = SlickDatabase.forConfig(cfg, journalConfig.slickConfiguration)

  override def beforeAll(): Unit = {
    dropCreate(schemaType)
    super.beforeAll()
  }

  private var senderProbe: TestProbe = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    senderProbe = TestProbe()
  }

  def snapshotStore: ActorRef =
    extension.snapshotStoreFor(null)

  "A snapshot store" must {
    "save big snapshots" in {
      val metadata = SnapshotMetadata(pid, 0)
      val bigSnapshot = for {
        i <- 1 to 10000
      } yield '0'
      snapshotStore.tell(SaveSnapshot(metadata, bigSnapshot.mkString("")), senderProbe.ref)
      senderProbe.expectMsgPF() { case SaveSnapshotSuccess(md) ⇒ md }
    }
  }

}
