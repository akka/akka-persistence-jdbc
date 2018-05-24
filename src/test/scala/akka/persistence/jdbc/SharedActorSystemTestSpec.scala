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
import akka.persistence.jdbc.config.{JournalConfig, ReadJournalConfig}
import akka.persistence.jdbc.query.javadsl.JdbcReadJournal
import akka.persistence.jdbc.util.{DropCreate, SlickExtension}
import akka.serialization.SerializationExtension
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory, ConfigValue}
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

abstract class SharedActorSystemTestSpec(val config: Config) extends SimpleSpec with DropCreate with BeforeAndAfterAll {

  def this(config: String = "postgres-application.conf", configOverrides: Map[String, ConfigValue] = Map.empty) =
    this(configOverrides.foldLeft(ConfigFactory.load(config)) {
      case (conf, (path, configValue)) => conf.withValue(path, configValue)
    })

  implicit lazy val system: ActorSystem = ActorSystem("test", config)
  implicit lazy val mat: Materializer = ActorMaterializer()

  implicit lazy val ec: ExecutionContext = system.dispatcher
  implicit val pc: PatienceConfig = PatienceConfig(timeout = 1.minute)
  implicit val timeout = Timeout(1.minute)

  lazy val serialization = SerializationExtension(system)

  val cfg = config.getConfig("jdbc-journal")
  val journalConfig = new JournalConfig(cfg)
  lazy val db = SlickExtension(system).database(cfg)
  val readJournalConfig = new ReadJournalConfig(config.getConfig(JdbcReadJournal.Identifier))

  override protected def afterAll(): Unit = {
    db.close()
    system.terminate().futureValue
  }
}
