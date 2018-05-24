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

import akka.persistence.CapabilityFlag
import akka.persistence.jdbc.config._
import akka.persistence.jdbc.util.Schema._
import akka.persistence.jdbc.util.{ ClasspathResources, DropCreate, SlickDatabase, SlickExtension }
import akka.persistence.journal.JournalSpec
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }

import scala.concurrent.duration._

abstract class JdbcJournalSpec(config: Config, schemaType: SchemaType) extends JournalSpec(config)
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with ScalaFutures
  with ClasspathResources
  with DropCreate {

  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = true

  implicit val pc: PatienceConfig = PatienceConfig(timeout = 10.seconds)

  implicit lazy val ec = system.dispatcher

  lazy val cfg = system.settings.config.getConfig("jdbc-journal")

  lazy val journalConfig = new JournalConfig(cfg)

  lazy val db = SlickExtension(system).database(cfg)

  override def beforeAll(): Unit = {
    dropCreate(schemaType)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    db.close()
    super.afterAll()
  }
}

class PostgresJournalSpec extends JdbcJournalSpec(ConfigFactory.load("postgres-application.conf"), Postgres())
class PostgresJournalSpecSharedDb extends JdbcJournalSpec(ConfigFactory.load("postgres-shared-db-application.conf"), Postgres())
class PostgresJournalSpecPhysicalDelete extends JdbcJournalSpec(ConfigFactory.load("postgres-application.conf")
  .withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false)), Postgres())

class MySQLJournalSpecSharedDb extends JdbcJournalSpec(ConfigFactory.load("mysql-shared-db-application.conf"), MySQL())
class MySQLJournalSpecPhysicalDelete extends JdbcJournalSpec(ConfigFactory.load("mysql-application.conf")
  .withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false)), MySQL())

class OracleJournalSpec extends JdbcJournalSpec(ConfigFactory.load("oracle-application.conf"), Oracle())
class OracleJournalSpecSharedDb extends JdbcJournalSpec(ConfigFactory.load("oracle-shared-db-application.conf"), Oracle())
class OracleJournalSpecPhysicalDelete extends JdbcJournalSpec(ConfigFactory.load("oracle-application.conf")
  .withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false)), Oracle())

class H2JournalSpec extends JdbcJournalSpec(ConfigFactory.load("h2-application.conf"), H2())
class H2JournalSpecSharedDb extends JdbcJournalSpec(ConfigFactory.load("h2-shared-db-application.conf"), H2())
class H2JournalSpecPhysicalDelete extends JdbcJournalSpec(ConfigFactory.load("h2-application.conf")
  .withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false)), H2())
