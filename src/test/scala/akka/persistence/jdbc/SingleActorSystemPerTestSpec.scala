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

import akka.actor.ActorSystem
import akka.persistence.jdbc.config.{ JournalConfig, ReadJournalConfig, SlickConfiguration }
import akka.persistence.jdbc.query.javadsl.JdbcReadJournal
import akka.persistence.jdbc.util.{ DropCreate, SlickDatabase, SlickDriver }
import akka.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory, ConfigValue }
import org.scalatest.BeforeAndAfterEach
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.duration._

abstract class SingleActorSystemPerTestSpec(val config: Config) extends SimpleSpec with DropCreate with BeforeAndAfterEach {

  def this(config: String = "postgres-application.conf", configOverrides: Map[String, ConfigValue] = Map.empty) =
    this(configOverrides.foldLeft(ConfigFactory.load(config)) {
      case (conf, (path, configValue)) => conf.withValue(path, configValue)
    })

  implicit val pc: PatienceConfig = PatienceConfig(timeout = 1.minute)
  implicit val timeout: Timeout = Timeout(1.minute)

  val cfg = config.getConfig("jdbc-journal")
  val journalConfig = new JournalConfig(cfg)
  val profile = if (cfg.hasPath("slick.profile")) {
    SlickDatabase.profile(cfg, "slick")
  } else SlickDatabase.profile(config, "akka-persistence-jdbc.shared-databases.slick")
  val readJournalConfig = new ReadJournalConfig(config.getConfig(JdbcReadJournal.Identifier))

  // The db is initialized in the before and after each bocks
  var dbOpt: Option[Database] = None
  def db: Database = {
    dbOpt.getOrElse {
      val newDb = if (cfg.hasPath("slick.profile")) {
        SlickDatabase.database(cfg, new SlickConfiguration(cfg.getConfig("slick")), "slick.db")
      } else SlickDatabase.database(config, new SlickConfiguration(config.getConfig("akka-persistence-jdbc.shared-databases.slick")), "akka-persistence-jdbc.shared-databases.slick.db")

      dbOpt = Some(newDb)
      newDb
    }
  }

  def closeDb(): Unit = {
    dbOpt.foreach(_.close())
    dbOpt = None
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    closeDb()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    closeDb()
  }

  def withActorSystem(f: ActorSystem => Unit): Unit = {
    implicit val system: ActorSystem = ActorSystem("test", config)
    f(system)
    system.terminate().futureValue
  }
}
