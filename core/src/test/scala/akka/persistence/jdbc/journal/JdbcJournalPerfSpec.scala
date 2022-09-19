/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.journal

import akka.actor.Props
import akka.persistence.CapabilityFlag
import akka.persistence.jdbc.config._
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.testkit.internal.{ H2, SchemaType }
import akka.persistence.jdbc.util.{ ClasspathResources, DropCreate }
import akka.persistence.journal.JournalPerfSpec
import akka.persistence.journal.JournalPerfSpec.{ BenchActor, Cmd, ResetCounter }
import akka.testkit.TestProbe
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._

abstract class JdbcJournalPerfSpec(config: Config, schemaType: SchemaType)
    extends JournalPerfSpec(config)
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with ScalaFutures
    with ClasspathResources
    with DropCreate {
  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = true

  implicit lazy val ec = system.dispatcher

  implicit def pc: PatienceConfig = PatienceConfig(timeout = 10.minutes)

  override def eventsCount: Int = 1000

  override def awaitDurationMillis: Long = 10.minutes.toMillis

  override def measurementIterations: Int = 1

  lazy val cfg = system.settings.config.getConfig("jdbc-journal")

  lazy val journalConfig = new JournalConfig(cfg)

  lazy val db = SlickExtension(system).database(cfg).database

  override def beforeAll(): Unit = {
    dropAndCreate(schemaType)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    db.close()
    super.afterAll()
  }

  def actorCount = 100

  private val commands = Vector(1 to eventsCount: _*)

  "A PersistentActor's performance" must {
    s"measure: persist()-ing $eventsCount events for $actorCount actors" in {
      val testProbe = TestProbe()
      val replyAfter = eventsCount
      def createBenchActor(actorNumber: Int) =
        system.actorOf(Props(classOf[BenchActor], s"$pid--$actorNumber", testProbe.ref, replyAfter))
      val actors = 1.to(actorCount).map(createBenchActor)

      measure(d => s"Persist()-ing $eventsCount * $actorCount took ${d.toMillis} ms") {
        for (cmd <- commands; actor <- actors) {
          actor ! Cmd("p", cmd)
        }
        for (_ <- actors) {
          testProbe.expectMsg(awaitDurationMillis.millis, commands.last)
        }
        for (actor <- actors) {
          actor ! ResetCounter
        }
      }
    }
  }

  "A PersistentActor's performance" must {
    s"measure: persistAsync()-ing $eventsCount events for $actorCount actors" in {
      val testProbe = TestProbe()
      val replyAfter = eventsCount
      def createBenchActor(actorNumber: Int) =
        system.actorOf(Props(classOf[BenchActor], s"$pid--$actorNumber", testProbe.ref, replyAfter))
      val actors = 1.to(actorCount).map(createBenchActor)

      measure(d => s"persistAsync()-ing $eventsCount * $actorCount took ${d.toMillis} ms") {
        for (cmd <- commands; actor <- actors) {
          actor ! Cmd("pa", cmd)
        }
        for (_ <- actors) {
          testProbe.expectMsg(awaitDurationMillis.millis, commands.last)
        }
        for (actor <- actors) {
          actor ! ResetCounter
        }
      }
    }
  }
}

class H2JournalPerfSpec extends JdbcJournalPerfSpec(ConfigFactory.load("h2-application.conf"), H2)

class H2JournalPerfSpecSharedDb extends JdbcJournalPerfSpec(ConfigFactory.load("h2-shared-db-application.conf"), H2)
