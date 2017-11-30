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
import akka.persistence.jdbc.util.{ClasspathResources, DropCreate, SlickDatabase}
import akka.persistence.journal.JournalSpec
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

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

  lazy val db = SlickDatabase.forConfig(cfg, journalConfig.slickConfiguration)

  override def beforeAll(): Unit = {
    dropCreate(schemaType)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    db.close()
    super.afterAll()
  }
}

/**
 * Use the postgres DDL script that is available on the website, for some reason slick generates incorrect DDL,
 * but the Slick Tables definition must not change, else it breaks the UPSERT feature...
 */
class PostgresJournalSpec extends JdbcJournalSpec(ConfigFactory.load("postgres-application.conf"), Postgres())

/**
 * Does not (yet) work because Slick generates double quotes to escape field names
 * for some reason when creating the DDL
 */
class MySQLJournalSpec extends JdbcJournalSpec(ConfigFactory.load("mysql-application.conf"), MySQL())

class OracleJournalSpec extends JdbcJournalSpec(ConfigFactory.load("oracle-application.conf"), Oracle())

class H2JournalSpec extends JdbcJournalSpec(ConfigFactory.load("h2-application.conf"), H2())
