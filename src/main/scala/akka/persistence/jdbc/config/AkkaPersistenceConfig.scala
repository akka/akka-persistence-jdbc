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

package akka.persistence.jdbc.config

import akka.persistence.jdbc.util.ConfigOps._
import com.typesafe.config.Config

class SlickConfiguration(config: Config) {
  private val cfg = config.getConfig("slick")
  val slickDriver: String = cfg.as[String]("driver", "slick.driver.PostgresDriver")
  val jndiName: Option[String] = cfg.as[String]("jndiName").trim
  val jndiDbName: Option[String] = cfg.as[String]("jndiDbName")
  assert(slickDriver.nonEmpty, "slick.driver should not be empty.")
}

class JournalTableColumnNames(config: Config) {
  private val cfg = config.getConfig("tables.journal.columnNames")
  val persistenceId: String = cfg.as[String]("persistenceId", "persistence_id")
  val sequenceNumber: String = cfg.as[String]("sequenceNumber", "sequence_nr")
  val created: String = cfg.as[String]("created", "created")
  val tags: String = cfg.as[String]("tags", "tags")
  val message: String = cfg.as[String]("message", "message")
}

class JournalTableConfiguration(config: Config) {
  private val cfg = config.getConfig("tables.journal")
  val tableName: String = cfg.as[String]("tableName", "journal")
  val schemaName: Option[String] = cfg.as[String]("schemaName").trim
  val columnNames: JournalTableColumnNames = new JournalTableColumnNames(config)
}

class DeletedToTableColumnNames(config: Config) {
  private val cfg = config.getConfig("tables.deletedTo.columnNames")
  val persistenceId: String = cfg.as[String]("persistenceId", "persistence_id")
  val deletedTo: String = cfg.as[String]("deletedTo", "deleted_to")
}

class DeletedToTableConfiguration(config: Config) {
  private val cfg = config.getConfig("tables.deletedTo")
  val tableName: String = cfg.as[String]("tableName", "journal")
  val schemaName: Option[String] = cfg.as[String]("schemaName").trim
  val columnNames: DeletedToTableColumnNames = new DeletedToTableColumnNames(config)
}

class SnapshotTableColumnNames(config: Config) {
  private val cfg = config.getConfig("tables.snapshot.columnNames")
  val persistenceId: String = cfg.as[String]("persistenceId", "persistence_id")
  val sequenceNumber: String = cfg.as[String]("sequenceNumber", "sequence_nr")
  val created: String = cfg.as[String]("created", "created")
  val snapshot: String = cfg.as[String]("snapshot", "snapshot")
}

class SnapshotTableConfiguration(config: Config) {
  val cfg = config.getConfig("tables.snapshot")
  val tableName: String = cfg.as[String]("tableName", "snapshot")
  val schemaName: Option[String] = cfg.as[String]("schemaName").trim
  val columnNames: SnapshotTableColumnNames = new SnapshotTableColumnNames(config)
}

class PluginConfig(config: Config) {
  val tagSeparator: String = config.as[String]("tagSeparator", ",")
  val serialization: Boolean = config.getBoolean("serialization")
  val dao: String = config.getString("dao")
}

// aggregations

class JournalConfig(config: Config) {
  val slickConfiguration = new SlickConfiguration(config)
  val journalTableConfiguration = new JournalTableConfiguration(config)
  val deletedToTableConfiguration = new DeletedToTableConfiguration(config)
  val pluginConfig = new PluginConfig(config)
}

class SnapshotConfig(config: Config) {
  val slickConfiguration = new SlickConfiguration(config)
  val snapshotTableConfiguration = new SnapshotTableConfiguration(config)
  val pluginConfig = new PluginConfig(config)
}

class ReadJournalConfig(config: Config) {
  val slickConfiguration = new SlickConfiguration(config)
  val journalTableConfiguration = new JournalTableConfiguration(config)
  val pluginConfig = new PluginConfig(config)
}
