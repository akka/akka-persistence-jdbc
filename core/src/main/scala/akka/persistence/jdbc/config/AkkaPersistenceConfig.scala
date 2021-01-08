/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.config

import akka.persistence.jdbc.util.ConfigOps._
import com.typesafe.config.Config

import scala.concurrent.duration._

object ConfigKeys {
  val useSharedDb = "use-shared-db"
}

class SlickConfiguration(config: Config) {
  val jndiName: Option[String] = config.as[String]("jndiName").trim
  val jndiDbName: Option[String] = config.as[String]("jndiDbName")
  override def toString: String = s"SlickConfiguration($jndiName,$jndiDbName)"
}

class LegacyJournalTableColumnNames(config: Config) {
  private val cfg = config.asConfig("tables.legacy_journal.columnNames")
  val ordering: String = cfg.as[String]("ordering", "ordering")
  val deleted: String = cfg.as[String]("deleted", "deleted")
  val persistenceId: String = cfg.as[String]("persistenceId", "persistence_id")
  val sequenceNumber: String = cfg.as[String]("sequenceNumber", "sequence_number")
  val created: String = cfg.as[String]("created", "created")
  val tags: String = cfg.as[String]("tags", "tags")
  val message: String = cfg.as[String]("message", "message")
  override def toString: String = s"JournalTableColumnNames($persistenceId,$sequenceNumber,$created,$tags,$message)"
}

class EventJournalTableColumnNames(config: Config) {
  private val cfg = config.asConfig("tables.event_journal.columnNames")
  val ordering: String = cfg.as[String]("ordering", "ordering")
  val deleted: String = cfg.as[String]("deleted", "deleted")
  val persistenceId: String = cfg.as[String]("persistenceId", "persistence_id")
  val sequenceNumber: String = cfg.as[String]("sequenceNumber", "sequence_number")
  val writer: String = cfg.as[String]("writer", "writer")
  val writeTimestamp: String = cfg.as[String]("writeTimestamp", "write_timestamp")
  val adapterManifest: String = cfg.as[String]("adapterManifest", "adapter_manifest")

  val eventPayload: String = cfg.as[String]("eventPayload", "event_payload")
  val eventSerId: String = cfg.as[String]("eventSerId", "event_ser_id")
  val eventSerManifest: String = cfg.as[String]("eventSerManifest", "event_ser_manifest")

  val metaPayload: String = cfg.as[String]("metaPayload", "meta_payload")
  val metaSerId: String = cfg.as[String]("metaSerId", "meta_ser_id")
  val metaSerManifest: String = cfg.as[String]("metaSerManifest", "meta_ser_manifest")
}

class EventTagTableColumnNames(config: Config) {
  private val cfg = config.asConfig("tables.event_tag.columnNames")
  val eventId: String = cfg.as[String]("eventId", "event_id")
  val tag: String = cfg.as[String]("tag", "tag")
}

class LegacyJournalTableConfiguration(config: Config) {
  private val cfg = config.asConfig("tables.legacy_journal")
  val tableName: String = cfg.as[String]("tableName", "journal")
  val schemaName: Option[String] = cfg.as[String]("schemaName").trim
  val columnNames: LegacyJournalTableColumnNames = new LegacyJournalTableColumnNames(config)
  override def toString: String = s"LegacyJournalTableConfiguration($tableName,$schemaName,$columnNames)"
}

class EventJournalTableConfiguration(config: Config) {
  private val cfg = config.asConfig("tables.event_journal")
  val tableName: String = cfg.as[String]("tableName", "event_journal")
  val schemaName: Option[String] = cfg.as[String]("schemaName").trim
  val columnNames: EventJournalTableColumnNames = new EventJournalTableColumnNames(config)
  override def toString: String = s"EventJournalTableConfiguration($tableName,$schemaName,$columnNames)"
}
class EventTagTableConfiguration(config: Config) {
  private val cfg = config.asConfig("tables.event_tag")
  val tableName: String = cfg.as[String]("tableName", "event_tag")
  val schemaName: Option[String] = cfg.as[String]("schemaName").trim
  val columnNames: EventTagTableColumnNames = new EventTagTableColumnNames(config)
}
class LegacySnapshotTableColumnNames(config: Config) {
  private val cfg = config.asConfig("tables.legacy_snapshot.columnNames")
  val persistenceId: String = cfg.as[String]("persistenceId", "persistence_id")
  val sequenceNumber: String = cfg.as[String]("sequenceNumber", "sequence_number")
  val created: String = cfg.as[String]("created", "created")
  val snapshot: String = cfg.as[String]("snapshot", "snapshot")
  override def toString: String = s"SnapshotTableColumnNames($persistenceId,$sequenceNumber,$created,$snapshot)"
}

class SnapshotTableColumnNames(config: Config) {
  private val cfg = config.asConfig("tables.snapshot.columnNames")
  val persistenceId: String = cfg.as[String]("persistenceId", "persistence_id")
  val sequenceNumber: String = cfg.as[String]("sequenceNumber", "sequence_number")
  val created: String = cfg.as[String]("created", "created")

  val snapshotPayload: String = cfg.as[String]("snapshotPayload", "snapshot_payload")
  val snapshotSerId: String = cfg.as[String]("snapshotSerId", "snapshot_ser_id")
  val snapshotSerManifest: String = cfg.as[String]("snapshotSerManifest", "snapshot_manifest")

  val metaPayload: String = cfg.as[String]("metaPayload", "meta_payload")
  val metaSerId: String = cfg.as[String]("metaSerId", "meta_ser_id")
  val metaSerManifest: String = cfg.as[String]("metaSerManifest", "meta_manifest")
}

class LegacySnapshotTableConfiguration(config: Config) {
  private val cfg = config.asConfig("tables.legacy_snapshot")
  val tableName: String = cfg.as[String]("tableName", "snapshot")
  val schemaName: Option[String] = cfg.as[String]("schemaName").trim
  val columnNames: LegacySnapshotTableColumnNames = new LegacySnapshotTableColumnNames(config)
  override def toString: String = s"LegacySnapshotTableConfiguration($tableName,$schemaName,$columnNames)"
}

class SnapshotTableConfiguration(config: Config) {
  private val cfg = config.asConfig("tables.snapshot")
  val tableName: String = cfg.as[String]("tableName", "snapshot")
  val schemaName: Option[String] = cfg.as[String]("schemaName").trim
  val columnNames: SnapshotTableColumnNames = new SnapshotTableColumnNames(config)
  override def toString: String = s"SnapshotTableConfiguration($tableName,$schemaName,$columnNames)"
}

class JournalPluginConfig(config: Config) {
  val tagSeparator: String = config.as[String]("tagSeparator", ",")
  val dao: String = config.as[String]("dao", "akka.persistence.jdbc.dao.bytea.journal.ByteArrayJournalDao")
  override def toString: String = s"JournalPluginConfig($tagSeparator,$dao)"
}

class BaseDaoConfig(config: Config) {
  val bufferSize: Int = config.asInt("bufferSize", 1000)
  val batchSize: Int = config.asInt("batchSize", 400)
  val replayBatchSize: Int = config.asInt("replayBatchSize", 400)
  val parallelism: Int = config.asInt("parallelism", 8)
  val logicalDelete: Boolean = config.asBoolean("logicalDelete", default = true)
  override def toString: String = s"BaseDaoConfig($bufferSize,$batchSize,$parallelism,$logicalDelete)"
}

class ReadJournalPluginConfig(config: Config) {
  val tagSeparator: String = config.as[String]("tagSeparator", ",")
  val dao: String = config.as[String]("dao", "akka.persistence.jdbc.dao.bytea.readjournal.ByteArrayReadJournalDao")
  override def toString: String = s"ReadJournalPluginConfig($tagSeparator,$dao)"
}

class SnapshotPluginConfig(config: Config) {
  val dao: String = config.as[String]("dao", "akka.persistence.jdbc.dao.bytea.snapshot.ByteArraySnapshotDao")
  override def toString: String = s"SnapshotPluginConfig($dao)"
}

// aggregations

class JournalConfig(config: Config) {
  val journalTableConfiguration = new LegacyJournalTableConfiguration(config)
  val eventJournalTableConfiguration = new EventJournalTableConfiguration(config)
  val eventTagTableConfiguration = new EventTagTableConfiguration(config)
  val pluginConfig = new JournalPluginConfig(config)
  val daoConfig = new BaseDaoConfig(config)
  val useSharedDb: Option[String] = config.asOptionalNonEmptyString(ConfigKeys.useSharedDb)
  override def toString: String = s"JournalConfig($journalTableConfiguration,$pluginConfig,$useSharedDb)"
}

class SnapshotConfig(config: Config) {
  val legacySnapshotTableConfiguration = new LegacySnapshotTableConfiguration(config)
  val snapshotTableConfiguration = new SnapshotTableConfiguration(config)
  val pluginConfig = new SnapshotPluginConfig(config)
  val useSharedDb: Option[String] = config.asOptionalNonEmptyString(ConfigKeys.useSharedDb)
  override def toString: String = s"SnapshotConfig($snapshotTableConfiguration,$pluginConfig,$useSharedDb)"
}

object JournalSequenceRetrievalConfig {
  def apply(config: Config): JournalSequenceRetrievalConfig =
    JournalSequenceRetrievalConfig(
      batchSize = config.asInt("journal-sequence-retrieval.batch-size", 10000),
      maxTries = config.asInt("journal-sequence-retrieval.max-tries", 10),
      queryDelay = config.asFiniteDuration("journal-sequence-retrieval.query-delay", 1.second),
      maxBackoffQueryDelay = config.asFiniteDuration("journal-sequence-retrieval.max-backoff-query-delay", 1.minute),
      askTimeout = config.asFiniteDuration("journal-sequence-retrieval.ask-timeout", 1.second))
}
case class JournalSequenceRetrievalConfig(
    batchSize: Int,
    maxTries: Int,
    queryDelay: FiniteDuration,
    maxBackoffQueryDelay: FiniteDuration,
    askTimeout: FiniteDuration)

class ReadJournalConfig(config: Config) {
  val journalTableConfiguration = new LegacyJournalTableConfiguration(config)
  val eventJournalTableConfiguration = new EventJournalTableConfiguration(config)
  val eventTagTableConfiguration = new EventTagTableConfiguration(config)
  val journalSequenceRetrievalConfiguration = JournalSequenceRetrievalConfig(config)
  val pluginConfig = new ReadJournalPluginConfig(config)
  val refreshInterval: FiniteDuration = config.asFiniteDuration("refresh-interval", 1.second)
  val maxBufferSize: Int = config.as[String]("max-buffer-size", "500").toInt
  val addShutdownHook: Boolean = config.asBoolean("add-shutdown-hook", true)
  val includeDeleted: Boolean = config.as[Boolean]("includeLogicallyDeleted", true)

  override def toString: String =
    s"ReadJournalConfig($journalTableConfiguration,$pluginConfig,$refreshInterval,$maxBufferSize,$addShutdownHook,$includeDeleted)"
}
