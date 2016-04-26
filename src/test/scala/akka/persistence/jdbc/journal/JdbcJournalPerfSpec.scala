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
import akka.persistence.jdbc.dao.bytea.JournalTables
import akka.persistence.jdbc.extension.{ AkkaPersistenceConfig, DeletedToTableConfiguration, JournalTableConfiguration, SlickDatabase }
import akka.persistence.jdbc.util.Schema.Postgres
import akka.persistence.jdbc.util.{ ClasspathResources, DropCreate, SlickDriver }
import akka.persistence.journal.JournalPerfSpec
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }
import slick.driver.JdbcProfile

import scala.concurrent.duration._

abstract class JdbcJournalPerfSpec(config: Config) extends JournalPerfSpec(config)
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with ScalaFutures
    with JournalTables
    with ClasspathResources
    with DropCreate {

  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = false

  implicit val pc: PatienceConfig = PatienceConfig(timeout = 10.seconds)

  implicit val ec = system.dispatcher

  val db = SlickDatabase(system).db

  override def journalTableCfg: JournalTableConfiguration =
    AkkaPersistenceConfig(system).journalTableConfiguration

  override def deletedToTableCfg: DeletedToTableConfiguration =
    AkkaPersistenceConfig(system).deletedToTableConfiguration

  override val profile: JdbcProfile =
    SlickDriver.forDriverName(AkkaPersistenceConfig(system).slickConfiguration.slickDriver)
}

class PostgresJournalPerfSpec extends JdbcJournalPerfSpec(ConfigFactory.load("postgres-application.conf")) {
  dropCreate(Postgres())
}
