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

package akka.persistence.jdbc

import java.util.UUID

import akka.actor.{ActorRef, PoisonPill}
import akka.event.{Logging, LoggingAdapter}
import akka.persistence.jdbc.config.JournalConfig
import akka.persistence.jdbc.util.{DropCreate, SlickDatabase}
import akka.serialization.SerializationExtension
import akka.testkit.TestProbe
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

abstract class TestSpec(override val config: Config) extends SimpleSpec with MaterializerSpec with DropCreate with BeforeAndAfterAll {

  def this(config: String = "postgres-application.conf") = this(ConfigFactory.load(config))

  implicit val ec: ExecutionContextExecutor = system.dispatcher
  val log: LoggingAdapter = Logging(system, this.getClass)
  implicit val pc: PatienceConfig = PatienceConfig(timeout = 60.seconds)
  implicit val timeout = Timeout(30.seconds)
  val serialization = SerializationExtension(system)

  val cfg = system.settings.config.getConfig("jdbc-journal")
  val journalConfig = new JournalConfig(cfg)
  val db = SlickDatabase.forConfig(cfg, journalConfig.slickConfiguration)

  /**
   * TestKit-based probe which allows sending, reception and reply.
   */
  def probe: TestProbe = TestProbe()

  /**
   * Returns a random UUID
   */
  def randomId = UUID.randomUUID.toString.take(5)

  /**
   * Sends the PoisonPill command to an actor and waits for it to die
   */
  def cleanup(actors: ActorRef*): Unit = {
    val tp = probe
    actors.foreach { (actor: ActorRef) ⇒
      tp watch actor
      actor ! PoisonPill
      tp.expectTerminated(actor)
    }
  }

  implicit class PimpedByteArray(self: Array[Byte]) {
    def getString: String = new String(self)
  }

  implicit class PimpedFuture[T](self: Future[T]) {
    def toTry: Try[T] = Try(self.futureValue)
  }

  override protected def afterAll(): Unit = {
    db.close()
    system.terminate().toTry should be a 'success
  }
}
