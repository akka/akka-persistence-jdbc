/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.migrator

import akka.actor.ActorSystem
import akka.persistence.SnapshotMetadata
import akka.persistence.jdbc.config.{ ReadJournalConfig, SnapshotConfig }
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.query.dao.legacy.ByteArrayReadJournalDao
import akka.persistence.jdbc.snapshot.dao.DefaultSnapshotDao
import akka.persistence.jdbc.snapshot.dao.legacy.{ ByteArraySnapshotSerializer, SnapshotQueries }
import akka.persistence.jdbc.snapshot.dao.legacy.SnapshotTables.SnapshotRow
import akka.serialization.{ Serialization, SerializationExtension }
import akka.stream.scaladsl.{ Sink, Source }
import akka.Done
import akka.persistence.jdbc.migrator.SnapshotMigrator.{ NoParallelism, SnapshotStoreConfig }
import org.slf4j.{ Logger, LoggerFactory }
import slick.jdbc
import slick.jdbc.{ JdbcBackend, JdbcProfile }

import scala.concurrent.Future

/**
 * This will help migrate the legacy snapshot data onto the new snapshot schema with the
 * appropriate serialization
 *
 * @param system the actor system
 */
case class SnapshotMigrator(profile: JdbcProfile)(implicit system: ActorSystem) {
  val log: Logger = LoggerFactory.getLogger(getClass)

  import system.dispatcher
  import profile.api._

  private val snapshotConfig: SnapshotConfig = new SnapshotConfig(system.settings.config.getConfig(SnapshotStoreConfig))
  private val readJournalConfig: ReadJournalConfig = new ReadJournalConfig(
    system.settings.config.getConfig(JournalMigrator.ReadJournalConfig))

  private val snapshotDB: jdbc.JdbcBackend.Database =
    SlickExtension(system).database(system.settings.config.getConfig(SnapshotStoreConfig)).database

  private val journalDB: JdbcBackend.Database =
    SlickExtension(system).database(system.settings.config.getConfig(JournalMigrator.ReadJournalConfig)).database

  private val serialization: Serialization = SerializationExtension(system)
  private val queries: SnapshotQueries = new SnapshotQueries(profile, snapshotConfig.legacySnapshotTableConfiguration)
  private val serializer: ByteArraySnapshotSerializer = new ByteArraySnapshotSerializer(serialization)

  // get the instance if the default snapshot dao
  private val defaultSnapshotDao: DefaultSnapshotDao =
    new DefaultSnapshotDao(snapshotDB, profile, snapshotConfig, serialization)

  // get the instance of the legacy journal DAO
  private val legacyJournalDao: ByteArrayReadJournalDao =
    new ByteArrayReadJournalDao(journalDB, profile, readJournalConfig, SerializationExtension(system))

  private def toSnapshotData(row: SnapshotRow): (SnapshotMetadata, Any) = serializer.deserialize(row).get

  /**
   * migrate the latest snapshot data
   */
  def migrateLatest(): Future[Done] = {
    legacyJournalDao
      .allPersistenceIdsSource(Long.MaxValue)
      .mapAsync(NoParallelism) { persistenceId =>
        // let us fetch the latest snapshot for each persistenceId
        snapshotDB.run(queries.selectLatestByPersistenceId(persistenceId).result).map { rows =>
          rows.headOption.map(toSnapshotData).map { case (metadata, value) =>
            log.debug(s"migrating snapshot for ${metadata.toString}")
            defaultSnapshotDao.save(metadata, value)
          }
        }
      }
      .runWith(Sink.ignore)
  }

  /**
   * migrate all the legacy snapshot schema data into the new snapshot schema
   */
  def migrateAll(): Future[Done] = Source
    .fromPublisher(snapshotDB.stream(queries.SnapshotTable.result))
    .mapAsync(NoParallelism) { record =>
      val (metadata, value) = toSnapshotData(record)
      log.debug(s"migrating snapshot for ${metadata.toString}")
      defaultSnapshotDao.save(metadata, value)
    }
    .run()
}

case object SnapshotMigrator {
  final val SnapshotStoreConfig: String = "jdbc-snapshot-store"
  final val NoParallelism: Int = 1
}
