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

import akka.actor.ActorSystem
import akka.persistence.jdbc.config.{JournalConfig, ReadJournalConfig}
import akka.persistence.jdbc.query.javadsl.JdbcReadJournal
import akka.persistence.jdbc.util.{DropCreate, SlickDatabase, SlickDriver}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory, ConfigValue}
import org.scalatest.BeforeAndAfterEach
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

abstract class SingleActorSystemPerTestSpec(val config: Config) extends SimpleSpec with DropCreate with BeforeAndAfterEach {

  def this(config: String = "postgres-application.conf", configOverrides: Map[String, ConfigValue] = Map.empty) =
    this(configOverrides.foldLeft(ConfigFactory.load(config)) {
      case (conf, (path, configValue)) => conf.withValue(path, configValue)
    })

  implicit val pc: PatienceConfig = PatienceConfig(timeout = 1.minute)
  implicit val timeout = Timeout(1.minute)

  val cfg = config.getConfig("jdbc-journal")
  val journalConfig = new JournalConfig(cfg)
  val profile = SlickDriver.forDriverName(cfg)
  val readJournalConfig = new ReadJournalConfig(config.getConfig(JdbcReadJournal.Identifier))

  // The db is initialized in the before and after each bocks
  var dbOpt: Option[Database] = None
  def db: Database = {
    dbOpt.getOrElse {
      val newDb = SlickDatabase.forConfig(cfg, journalConfig.slickConfiguration)
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

  def randomId = UUID.randomUUID.toString.take(5)

  def withActorSystem(f: ActorSystem => Unit): Unit = {
    implicit val system: ActorSystem = ActorSystem("test", config)
    f(system)
    system.terminate().futureValue
  }

  implicit class PimpedFuture[T](self: Future[T]) {
    def toTry: Try[T] = Try(self.futureValue)
  }
}
