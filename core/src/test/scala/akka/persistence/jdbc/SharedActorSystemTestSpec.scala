/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc

import akka.actor.ActorSystem
import akka.persistence.jdbc.config.{ JournalConfig, ReadJournalConfig }
import akka.persistence.jdbc.query.javadsl.JdbcReadJournal
import akka.persistence.jdbc.util.DropCreate
import akka.persistence.jdbc.db.SlickExtension
import akka.serialization.SerializationExtension
import akka.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory, ConfigValue }
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

abstract class SharedActorSystemTestSpec(val config: Config) extends SimpleSpec with DropCreate with BeforeAndAfterAll {
  def this(config: String = "postgres-application.conf", configOverrides: Map[String, ConfigValue] = Map.empty) =
    this(configOverrides.foldLeft(ConfigFactory.load(config)) { case (conf, (path, configValue)) =>
      conf.withValue(path, configValue)
    })

  implicit lazy val system: ActorSystem = ActorSystem("test", config)

  implicit lazy val ec: ExecutionContext = system.dispatcher
  implicit val pc: PatienceConfig = PatienceConfig(timeout = 1.minute)
  implicit val timeout = Timeout(1.minute)

  lazy val serialization = SerializationExtension(system)

  val cfg = config.getConfig("jdbc-journal")
  val journalConfig = new JournalConfig(cfg)
  lazy val db = SlickExtension(system).database(cfg).database
  val readJournalConfig = new ReadJournalConfig(config.getConfig(JdbcReadJournal.Identifier))

  override protected def afterAll(): Unit = {
    db.close()
    system.terminate().futureValue
  }
}
