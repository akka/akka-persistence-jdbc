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

package akka.persistence.jdbc.journal

import akka.actor.Props
import akka.persistence.CapabilityFlag
import akka.persistence.jdbc.config._
import akka.persistence.jdbc.util.Schema._
import akka.persistence.jdbc.util.{ ClasspathResources, DropCreate, SlickExtension }
import akka.persistence.journal.JournalPerfSpec
import akka.persistence.journal.JournalPerfSpec.{ BenchActor, Cmd, ResetCounter }
import akka.testkit.TestProbe
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }

import scala.concurrent.duration._

abstract class JdbcJournalPerfSpec(config: Config, schemaType: SchemaType) extends JournalPerfSpec(config)
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
    dropCreate(schemaType)
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
      def createBenchActor(actorNumber: Int) = system.actorOf(Props(classOf[BenchActor], s"$pid--$actorNumber", testProbe.ref, replyAfter))
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
      def createBenchActor(actorNumber: Int) = system.actorOf(Props(classOf[BenchActor], s"$pid--$actorNumber", testProbe.ref, replyAfter))
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

class PostgresJournalPerfSpec extends JdbcJournalPerfSpec(ConfigFactory.load("postgres-application.conf"), Postgres()) {
  override def eventsCount: Int = 100
}

class PostgresJournalPerfSpecSharedDb extends JdbcJournalPerfSpec(ConfigFactory.load("postgres-shared-db-application.conf"), Postgres()) {
  override def eventsCount: Int = 100
}

class PostgresJournalPerfSpecPhysicalDelete extends PostgresJournalPerfSpec {
  this.cfg.withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false))
}

class MySQLJournalPerfSpec extends JdbcJournalPerfSpec(ConfigFactory.load("mysql-application.conf"), MySQL()) {
  override def eventsCount: Int = 100
}

class MySQLJournalPerfSpecSharedDb extends JdbcJournalPerfSpec(ConfigFactory.load("mysql-shared-db-application.conf"), MySQL()) {
  override def eventsCount: Int = 100
}

class MySQLJournalPerfSpecPhysicalDelete extends MySQLJournalPerfSpec {
  this.cfg.withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false))
}

class OracleJournalPerfSpec extends JdbcJournalPerfSpec(ConfigFactory.load("oracle-application.conf"), Oracle()) {
  override def eventsCount: Int = 100
}

class OracleJournalPerfSpecSharedDb extends JdbcJournalPerfSpec(ConfigFactory.load("oracle-shared-db-application.conf"), Oracle()) {
  override def eventsCount: Int = 100
}

class OracleJournalPerfSpecPhysicalDelete extends OracleJournalPerfSpec {
  this.cfg.withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false))
}

class SqlServerJournalPerfSpec extends JdbcJournalPerfSpec(ConfigFactory.load("sqlserver-application.conf"), SqlServer()) {
  override def eventsCount: Int = 100
}

class SqlServerJournalPerfSpecSharedDb extends JdbcJournalPerfSpec(ConfigFactory.load("sqlserver-shared-db-application.conf"), SqlServer()) {
  override def eventsCount: Int = 100
}

class SqlServerJournalPerfSpecPhysicalDelete extends SqlServerJournalPerfSpec {
  this.cfg.withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false))
}

class H2JournalPerfSpec extends JdbcJournalPerfSpec(ConfigFactory.load("h2-application.conf"), H2())

class H2JournalPerfSpecSharedDb extends JdbcJournalPerfSpec(ConfigFactory.load("h2-shared-db-application.conf"), H2())

class H2JournalPerfSpecPhysicalDelete extends H2JournalPerfSpec {
  this.cfg.withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false))
}
