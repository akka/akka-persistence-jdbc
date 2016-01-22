/*
 * Copyright 2015 Dennis Vriend
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

package akka.persistence.jdbc.extension

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.event.{Logging, LoggingAdapter}
import com.typesafe.config.Config

import scala.util.Try

object AkkaPersistenceConfig extends ExtensionId[AkkaPersistenceConfigImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): AkkaPersistenceConfigImpl = new AkkaPersistenceConfigImpl()(system)

  override def lookup(): ExtensionId[_ <: Extension] = AkkaPersistenceConfig
}

case class SlickConfiguration(slickDriver: String = "") {
  def fromConfig(config: Config): SlickConfiguration = {
    val cfg = config.getConfig("akka-persistence-jdbc")
    this.copy(
      cfg.getString("slick.driver")
    )
  }
}

case class PersistenceQueryConfiguration(tagPrefix: String = "") {
  def fromConfig(config: Config): PersistenceQueryConfiguration = {
    val cfg = config.getConfig("akka-persistence-jdbc")
    this.copy(cfg.getString("query.tagPrefix"))
  }
}

case class JournalTableConfiguration(tableName: String = "", schema: Option[String] = None) {
  def fromConfig(config: Config): JournalTableConfiguration = {
    val cfg = config.getConfig("akka-persistence-jdbc.tables.journal")
    this.copy(
      cfg.getString("tableName"),
      Try(cfg.getString("schemaName")).toOption.map(_.trim).filter(_.nonEmpty)
    )
  }
}

case class DeletedToTableConfiguration(tableName: String = "", schema: Option[String] = None) {
  def fromConfig(config: Config): DeletedToTableConfiguration = {
    val cfg = config.getConfig("akka-persistence-jdbc.tables.deletedTo")
    this.copy(
      cfg.getString("tableName"),
      Try(cfg.getString("schemaName")).toOption.map(_.trim).filter(_.nonEmpty)
    )
  }
}

case class SnapshotTableConfiguration(tableName: String = "", schema: Option[String] = None) {
  def fromConfig(config: Config): SnapshotTableConfiguration = {
    val cfg = config.getConfig("akka-persistence-jdbc.tables.snapshot")
    this.copy(
      cfg.getString("tableName"),
      Try(cfg.getString("schemaName")).toOption.map(_.trim).filter(_.nonEmpty)
    )
  }
}

trait AkkaPersistenceConfig {

  def slickConfiguration: SlickConfiguration

  def persistenceQueryConfiguration: PersistenceQueryConfiguration

  def journalTableConfiguration: JournalTableConfiguration

  def deletedToTableConfiguration: DeletedToTableConfiguration

  def snapshotTableConfiguration: SnapshotTableConfiguration
}

class AkkaPersistenceConfigImpl()(implicit val system: ExtendedActorSystem) extends AkkaPersistenceConfig with Extension {
  val log: LoggingAdapter = Logging(system, this.getClass)

  override val slickConfiguration: SlickConfiguration =
    SlickConfiguration().fromConfig(system.settings.config)

  override val persistenceQueryConfiguration: PersistenceQueryConfiguration =
      PersistenceQueryConfiguration().fromConfig(system.settings.config)

  override def journalTableConfiguration: JournalTableConfiguration =
    JournalTableConfiguration().fromConfig(system.settings.config)

  override def deletedToTableConfiguration: DeletedToTableConfiguration =
    DeletedToTableConfiguration().fromConfig(system.settings.config)

  override def snapshotTableConfiguration: SnapshotTableConfiguration =
    SnapshotTableConfiguration().fromConfig(system.settings.config)

  def debugInfo: String =
    s"""
       | ====================================
       | Akka Persistence JDBC Configuration:
       | ====================================
       | $slickConfiguration
       | ====================================
       | $persistenceQueryConfiguration
       | ====================================
       | $journalTableConfiguration
       | ====================================
       | $deletedToTableConfiguration
       | ====================================
       | $snapshotTableConfiguration
       | ====================================
    """.stripMargin

  log.debug(debugInfo)
}