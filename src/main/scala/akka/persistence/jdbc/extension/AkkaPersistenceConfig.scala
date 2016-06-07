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

package akka.persistence.jdbc.extension

import java.util.concurrent.TimeUnit

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.event.{ Logging, LoggingAdapter }
import akka.persistence.jdbc.BuildInfo
import akka.persistence.jdbc.util.ConfigOps._
import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object AkkaPersistenceConfig extends ExtensionId[AkkaPersistenceConfigImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): AkkaPersistenceConfigImpl = new AkkaPersistenceConfigImpl()(system)

  override def lookup(): ExtensionId[_ <: Extension] = AkkaPersistenceConfig
}

object SlickConfiguration {
  def apply(cfg: Config): SlickConfiguration =
    cfg.withPath("akka-persistence-jdbc.slick") { cfg ⇒
      SlickConfiguration(
        cfg.as[String]("driver", "slick.driver.PostgresDriver"),
        cfg.as[String]("jndiName"),
        cfg.as[String]("jndiDbName")
      )
    }
}

case class SlickConfiguration(slickDriver: String, jndiName: Option[String], jndiDbName: Option[String])

object PersistenceQueryConfiguration {
  def apply(cfg: Config): PersistenceQueryConfiguration =
    cfg.withPath("akka-persistence-jdbc.query") { cfg ⇒
      PersistenceQueryConfiguration(cfg.as[String]("separator", ","))
    }
}

case class PersistenceQueryConfiguration(separator: String)

case class JournalTableColumnNames(persistenceId: String, sequenceNumber: String, created: String, tags: String, message: String)

case class JournalTableConfiguration(tableName: String, schemaName: Option[String], columnNames: JournalTableColumnNames)

object JournalTableConfiguration {
  def apply(cfg: Config): JournalTableConfiguration =
    cfg.withPath("akka-persistence-jdbc.tables.journal") { cfg ⇒
      JournalTableConfiguration(
        cfg.as[String]("tableName", "journal"),
        cfg.as[String]("schemaName").map(_.trim).filter(_.nonEmpty),
        cfg.withPath("columnNames") { cfg ⇒
          JournalTableColumnNames(
            cfg.as[String]("persistenceId", "persistence_id"),
            cfg.as[String]("sequenceNumber", "sequence_nr"),
            cfg.as[String]("created", "created"),
            cfg.as[String]("tags", "tags"),
            cfg.as[String]("message", "message")
          )
        }
      )
    }
}

case class DeletedToTableColumnNames(persistenceId: String, deletedTo: String)

case class DeletedToTableConfiguration(tableName: String, schemaName: Option[String], columnNames: DeletedToTableColumnNames)

object DeletedToTableConfiguration {
  def apply(cfg: Config): DeletedToTableConfiguration =
    cfg.withPath("akka-persistence-jdbc.tables.deletedTo") { cfg ⇒
      DeletedToTableConfiguration(
        cfg.as[String]("tableName", "journal"),
        cfg.as[String]("schemaName").map(_.trim).filter(_.nonEmpty),
        cfg.withPath("columnNames") { cfg ⇒
          DeletedToTableColumnNames(
            cfg.as[String]("persistenceId", "persistence_id"),
            cfg.as[String]("deletedTo", "deleted_to")
          )
        }
      )
    }
}

case class SnapshotTableColumnNames(persistenceId: String, sequenceNumber: String, created: String, snapshot: String)

case class SnapshotTableConfiguration(tableName: String, schemaName: Option[String], columnNames: SnapshotTableColumnNames)

object SnapshotTableConfiguration {
  def apply(cfg: Config): SnapshotTableConfiguration =
    cfg.withPath("akka-persistence-jdbc.tables.snapshot") { cfg ⇒
      SnapshotTableConfiguration(
        cfg.as[String]("tableName", "snapshot"),
        cfg.as[String]("schemaName").map(_.trim).filter(_.nonEmpty),
        cfg.withPath("columnNames") { cfg ⇒
          SnapshotTableColumnNames(
            cfg.as[String]("persistenceId", "persistence_id"),
            cfg.as[String]("sequenceNumber", "sequence_nr"),
            cfg.as[String]("created", "created"),
            cfg.as[String]("snapshot", "snapshot")
          )
        }
      )
    }
}

case class SerializationConfiguration(journal: Boolean, snapshot: Boolean, serializationIdentity: Option[Int])

object SerializationConfiguration {
  def apply(cfg: Config): SerializationConfiguration = SerializationConfiguration(
    cfg.getBoolean("akka-persistence-jdbc.serialization.journal"),
    cfg.getBoolean("akka-persistence-jdbc.serialization.snapshot"),
    Try(cfg.getInt("akka-persistence-jdbc.serialization.varchar.serializerIdentity")).toOption
  )
}

trait AkkaPersistenceConfig {

  def slickConfiguration: SlickConfiguration

  def persistenceQueryConfiguration: PersistenceQueryConfiguration

  def journalTableConfiguration: JournalTableConfiguration

  def deletedToTableConfiguration: DeletedToTableConfiguration

  def snapshotTableConfiguration: SnapshotTableConfiguration

  def inMemory: Boolean

  def inMemoryTimeout: FiniteDuration

  def serializationConfiguration: SerializationConfiguration
}

class AkkaPersistenceConfigImpl()(implicit val system: ExtendedActorSystem) extends AkkaPersistenceConfig with Extension {
  val log: LoggingAdapter = Logging(system, this.getClass)

  override val slickConfiguration: SlickConfiguration =
    if (inMemory) SlickConfiguration("", None, None)
    else SlickConfiguration(system.settings.config)

  override val persistenceQueryConfiguration: PersistenceQueryConfiguration =
    if (inMemory) PersistenceQueryConfiguration("")
    else PersistenceQueryConfiguration(system.settings.config)

  override def journalTableConfiguration: JournalTableConfiguration =
    if (inMemory) JournalTableConfiguration("", None, JournalTableColumnNames("", "", "", "", ""))
    else JournalTableConfiguration(system.settings.config)

  override def deletedToTableConfiguration: DeletedToTableConfiguration =
    if (inMemory) DeletedToTableConfiguration("", None, DeletedToTableColumnNames("", ""))
    else DeletedToTableConfiguration(system.settings.config)

  override def snapshotTableConfiguration: SnapshotTableConfiguration =
    if (inMemory) SnapshotTableConfiguration("", None, SnapshotTableColumnNames("", "", "", ""))
    else SnapshotTableConfiguration(system.settings.config)

  override def inMemory: Boolean =
    system.settings.config.getBoolean("akka-persistence-jdbc.inMemory")

  override def inMemoryTimeout: FiniteDuration =
    FiniteDuration(system.settings.config.getDuration("akka-persistence-jdbc.inMemoryTimeout", TimeUnit.SECONDS), TimeUnit.SECONDS)

  override def serializationConfiguration: SerializationConfiguration =
    SerializationConfiguration(system.settings.config)

  def debugInfo: String =
    s"""
       | ====================================
       | Akka Persistence JDBC Configuration:
       | ====================================
       | Version: ${BuildInfo.version}
       | BuildNumber: ${BuildInfo.buildInfoBuildNumber}
       | inMemory mode: $inMemory
       | inMemory timeout: $inMemoryTimeout
       | ====================================
       | jndiName: ${slickConfiguration.jndiName}
       | slickDriver: ${slickConfiguration.slickDriver}
       | ====================================
       | Tag separator: ${persistenceQueryConfiguration.separator}
       | === Serialization Confguration =====
       | journal: ${serializationConfiguration.journal}
       | snapshot: ${serializationConfiguration.snapshot}
       | === Journal Table Configuration ====
       | schemaName: ${journalTableConfiguration.schemaName.getOrElse("None")}
       | tableName: ${journalTableConfiguration.tableName}
       | columnNames:
       | - persistenceId: ${journalTableConfiguration.columnNames.persistenceId}
       | - sequenceNumber: ${journalTableConfiguration.columnNames.sequenceNumber}
       | - created: ${journalTableConfiguration.columnNames.created}
       | - tags: ${journalTableConfiguration.columnNames.tags}
       | - message: ${journalTableConfiguration.columnNames.message}
       | == DeletedTo Table Configuration ===
       | schemaName: ${deletedToTableConfiguration.schemaName.getOrElse("None")}
       | tableName: ${deletedToTableConfiguration.tableName}
       | columnNames:
       | - persistenceId: ${deletedToTableConfiguration.columnNames.persistenceId}
       | - deletedTo: ${deletedToTableConfiguration.columnNames.deletedTo}
       | === Snapshot Table Configuration ===
       | schemaName: ${snapshotTableConfiguration.schemaName.getOrElse("None")}
       | tableName: ${snapshotTableConfiguration.tableName}
       | columnNames:
       | - persistenceId: ${snapshotTableConfiguration.columnNames.persistenceId}
       | - sequenceNumber: ${snapshotTableConfiguration.columnNames.sequenceNumber}
       | - created: ${snapshotTableConfiguration.columnNames.created}
       | - snapshot: ${snapshotTableConfiguration.columnNames.snapshot}
       | ====================================
    """.stripMargin

  log.debug(debugInfo)
}
