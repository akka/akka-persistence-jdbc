/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.config

import akka.persistence.jdbc.util.ConfigOps._
import com.typesafe.config.Config

import scala.concurrent.duration._

object ConfigKeys {
  val useSharedDb = "use-shared-db"
}

class SlickConfiguration(config: Config) {
  val jndiName: Option[String] = config.asStringOption("jndiName")
  val jndiDbName: Option[String] = config.asStringOption("jndiDbName")
  override def toString: String = s"SlickConfiguration($jndiName,$jndiDbName)"
}

class LegacyJournalTableColumnNames(config: Config) {
  private val cfg = config.getConfig("tables.legacy_journal.columnNames")
  val ordering: String = cfg.getString("ordering")
  val deleted: String = cfg.getString("deleted")
  val persistenceId: String = cfg.getString("persistenceId")
  val sequenceNumber: String = cfg.getString("sequenceNumber")
  val created: String = cfg.getString("created")
  val tags: String = cfg.getString("tags")
  val message: String = cfg.getString("message")
  override def toString: String = s"JournalTableColumnNames($persistenceId,$sequenceNumber,$created,$tags,$message)"
}

class EventJournalTableColumnNames(config: Config) {
  private val cfg = config.getConfig("tables.event_journal.columnNames")
  val ordering: String = cfg.getString("ordering")
  val deleted: String = cfg.getString("deleted")
  val persistenceId: String = cfg.getString("persistenceId")
  val sequenceNumber: String = cfg.getString("sequenceNumber")
  val writer: String = cfg.getString("writer")
  val writeTimestamp: String = cfg.getString("writeTimestamp")
  val adapterManifest: String = cfg.getString("adapterManifest")

  val eventPayload: String = cfg.getString("eventPayload")
  val eventSerId: String = cfg.getString("eventSerId")
  val eventSerManifest: String = cfg.getString("eventSerManifest")

  val metaPayload: String = cfg.getString("metaPayload")
  val metaSerId: String = cfg.getString("metaSerId")
  val metaSerManifest: String = cfg.getString("metaSerManifest")
}

class EventTagTableColumnNames(config: Config) {
  private val cfg = config.getConfig("tables.event_tag.columnNames")
  val eventId: String = cfg.getString("eventId")
  val tag: String = cfg.getString("tag")
}

class LegacyJournalTableConfiguration(config: Config) {
  private val cfg = config.getConfig("tables.legacy_journal")
  val tableName: String = cfg.getString("tableName")
  val schemaName: Option[String] = cfg.asStringOption("schemaName")
  val columnNames: LegacyJournalTableColumnNames = new LegacyJournalTableColumnNames(config)
  override def toString: String = s"LegacyJournalTableConfiguration($tableName,$schemaName,$columnNames)"
}

class EventJournalTableConfiguration(config: Config) {
  private val cfg = config.getConfig("tables.event_journal")
  val tableName: String = cfg.getString("tableName")
  val schemaName: Option[String] = cfg.asStringOption("schemaName")
  val columnNames: EventJournalTableColumnNames = new EventJournalTableColumnNames(config)
  override def toString: String = s"EventJournalTableConfiguration($tableName,$schemaName,$columnNames)"
}
class EventTagTableConfiguration(config: Config) {
  private val cfg = config.getConfig("tables.event_tag")
  val tableName: String = cfg.getString("tableName")
  val schemaName: Option[String] = cfg.asStringOption("schemaName")
  val columnNames: EventTagTableColumnNames = new EventTagTableColumnNames(config)
}
class LegacySnapshotTableColumnNames(config: Config) {
  private val cfg = config.getConfig("tables.legacy_snapshot.columnNames")
  val persistenceId: String = cfg.getString("persistenceId")
  val sequenceNumber: String = cfg.getString("sequenceNumber")
  val created: String = cfg.getString("created")
  val snapshot: String = cfg.getString("snapshot")
  override def toString: String = s"SnapshotTableColumnNames($persistenceId,$sequenceNumber,$created,$snapshot)"
}

class SnapshotTableColumnNames(config: Config) {
  private val cfg = config.getConfig("tables.snapshot.columnNames")
  val persistenceId: String = cfg.getString("persistenceId")
  val sequenceNumber: String = cfg.getString("sequenceNumber")
  val created: String = cfg.getString("created")

  val snapshotPayload: String = cfg.getString("snapshotPayload")
  val snapshotSerId: String = cfg.getString("snapshotSerId")
  val snapshotSerManifest: String = cfg.getString("snapshotSerManifest")

  val metaPayload: String = cfg.getString("metaPayload")
  val metaSerId: String = cfg.getString("metaSerId")
  val metaSerManifest: String = cfg.getString("metaSerManifest")
}

class LegacySnapshotTableConfiguration(config: Config) {
  private val cfg = config.getConfig("tables.legacy_snapshot")
  val tableName: String = cfg.getString("tableName")
  val schemaName: Option[String] = cfg.asStringOption("schemaName")
  val columnNames: LegacySnapshotTableColumnNames = new LegacySnapshotTableColumnNames(config)
  override def toString: String = s"LegacySnapshotTableConfiguration($tableName,$schemaName,$columnNames)"
}

class SnapshotTableConfiguration(config: Config) {
  private val cfg = config.getConfig("tables.snapshot")
  val tableName: String = cfg.getString("tableName")
  val schemaName: Option[String] = cfg.asStringOption("schemaName")
  val columnNames: SnapshotTableColumnNames = new SnapshotTableColumnNames(config)
  override def toString: String = s"SnapshotTableConfiguration($tableName,$schemaName,$columnNames)"
}

class JournalPluginConfig(config: Config) {
  val tagSeparator: String = config.getString("tagSeparator")
  val dao: String = config.getString("dao")
  override def toString: String = s"JournalPluginConfig($tagSeparator,$dao)"
}

class BaseDaoConfig(config: Config) {
  val bufferSize: Int = config.getInt("bufferSize")
  val batchSize: Int = config.getInt("batchSize")
  val replayBatchSize: Int = config.getInt("replayBatchSize")
  val parallelism: Int = config.getInt("parallelism")
  val logicalDelete: Boolean = config.getBoolean("logicalDelete")
  override def toString: String = s"BaseDaoConfig($bufferSize,$batchSize,$parallelism,$logicalDelete)"
}

class ReadJournalPluginConfig(config: Config) {
  val tagSeparator: String = config.getString("tagSeparator")
  val dao: String = config.getString("dao")
  override def toString: String = s"ReadJournalPluginConfig($tagSeparator,$dao)"
}

class SnapshotPluginConfig(config: Config) {
  val dao: String = config.getString("dao")
  override def toString: String = s"SnapshotPluginConfig($dao)"
}

// aggregations

class JournalConfig(config: Config) {
  val journalTableConfiguration = new LegacyJournalTableConfiguration(config)
  val eventJournalTableConfiguration = new EventJournalTableConfiguration(config)
  val eventTagTableConfiguration = new EventTagTableConfiguration(config)
  val pluginConfig = new JournalPluginConfig(config)
  val daoConfig = new BaseDaoConfig(config)
  val useSharedDb: Option[String] = config.asStringOption(ConfigKeys.useSharedDb)
  override def toString: String = s"JournalConfig($journalTableConfiguration,$pluginConfig,$useSharedDb)"
}

class SnapshotConfig(config: Config) {
  val legacySnapshotTableConfiguration = new LegacySnapshotTableConfiguration(config)
  val snapshotTableConfiguration = new SnapshotTableConfiguration(config)
  val pluginConfig = new SnapshotPluginConfig(config)
  val useSharedDb: Option[String] = config.asStringOption(ConfigKeys.useSharedDb)
  override def toString: String = s"SnapshotConfig($snapshotTableConfiguration,$pluginConfig,$useSharedDb)"
}

object JournalSequenceRetrievalConfig {
  def apply(config: Config): JournalSequenceRetrievalConfig =
    JournalSequenceRetrievalConfig(
      batchSize = config.getInt("journal-sequence-retrieval.batch-size"),
      maxTries = config.getInt("journal-sequence-retrieval.max-tries"),
      queryDelay = config.asFiniteDuration("journal-sequence-retrieval.query-delay"),
      maxBackoffQueryDelay = config.asFiniteDuration("journal-sequence-retrieval.max-backoff-query-delay"),
      askTimeout = config.asFiniteDuration("journal-sequence-retrieval.ask-timeout"))
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
  val refreshInterval: FiniteDuration = config.asFiniteDuration("refresh-interval")
  val maxBufferSize: Int = config.getInt("max-buffer-size")
  val addShutdownHook: Boolean = config.getBoolean("add-shutdown-hook")
  val includeDeleted: Boolean = config.getBoolean("includeLogicallyDeleted")

  override def toString: String =
    s"ReadJournalConfig($journalTableConfiguration,$pluginConfig,$refreshInterval,$maxBufferSize,$addShutdownHook,$includeDeleted)"
}

class DurableStateTableColumnNames(config: Config) {
  private val cfg = config.getConfig("tables.state.columnNames")
  val globalOffset: String = cfg.getString("globalOffset")
  val persistenceId: String = cfg.getString("persistenceId")
  val statePayload: String = cfg.getString("statePayload")
  val tag: String = cfg.getString("tag")
  val revision: String = cfg.getString("revision")
  val stateSerId: String = cfg.getString("stateSerId")
  val stateSerManifest: String = cfg.getString("stateSerManifest")
  val stateTimestamp: String = cfg.getString("stateTimestamp")
}

class DurableStateTableConfiguration(config: Config) {
  private val cfg = config.getConfig("tables.state")
  val refreshInterval: FiniteDuration = config.asFiniteDuration("refreshInterval")
  val batchSize: Int = config.getInt("batchSize")
  val tableName: String = cfg.getString("tableName")
  val schemaName: Option[String] = cfg.asStringOption("schemaName")
  val columnNames: DurableStateTableColumnNames = new DurableStateTableColumnNames(config)
  val stateSequenceConfig = DurableStateSequenceRetrievalConfig(config)
  override def toString: String = s"DurableStateTableConfiguration($tableName,$schemaName,$columnNames)"
}

object DurableStateSequenceRetrievalConfig {
  def apply(config: Config): DurableStateSequenceRetrievalConfig =
    DurableStateSequenceRetrievalConfig(
      batchSize = config.getInt("durable-state-sequence-retrieval.batch-size"),
      maxTries = config.getInt("durable-state-sequence-retrieval.max-tries"),
      queryDelay = config.asFiniteDuration("durable-state-sequence-retrieval.query-delay"),
      maxBackoffQueryDelay = config.asFiniteDuration("durable-state-sequence-retrieval.max-backoff-query-delay"),
      askTimeout = config.asFiniteDuration("durable-state-sequence-retrieval.ask-timeout"),
      revisionCacheCapacity = config.getInt("durable-state-sequence-retrieval.revision-cache-capacity"))
}
case class DurableStateSequenceRetrievalConfig(
    batchSize: Int,
    maxTries: Int,
    queryDelay: FiniteDuration,
    maxBackoffQueryDelay: FiniteDuration,
    askTimeout: FiniteDuration,
    revisionCacheCapacity: Int)
