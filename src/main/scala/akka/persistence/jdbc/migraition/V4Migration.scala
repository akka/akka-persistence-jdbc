package akka.persistence.jdbc.migraition

import akka.actor.ActorSystem
import akka.event.Logging
import akka.persistence.jdbc.config.{ JournalConfig, JournalTableConfiguration, SnapshotConfig }
import akka.persistence.jdbc.journal.dao.{ ByteArrayJournalSerializer, JournalTables }
import akka.persistence.jdbc.snapshot.dao.{ ByteArraySnapshotSerializer, SnapshotTables }
import akka.persistence.jdbc.util.{ SlickDatabase, SlickDriver }
import akka.serialization.SerializationExtension
import com.typesafe.config.Config

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

class V4JournalMigration(cfg: Config, system: ActorSystem) extends JournalTables {

  private val jdbcJournalConfig: Config = cfg.getConfig("jdbc-journal")
  private val journalConfig = new JournalConfig(jdbcJournalConfig)
  private val log = Logging(system, classOf[V4JournalMigration])

  if (!journalConfig.journalTableConfiguration.hasMessageColumn) {
    throw new IllegalArgumentException("Journal table configuration does not have message column, cannot perform migration.")
  }

  override val profile = SlickDriver.forDriverName(jdbcJournalConfig)

  import profile.api._
  import system.dispatcher

  override def journalTableCfg: JournalTableConfiguration = journalConfig.journalTableConfiguration

  private val serializer = new ByteArrayJournalSerializer(SerializationExtension(system), journalConfig.pluginConfig.tagSeparator,
    false)

  /**
   * The number of rows migrated per transaction.
   */
  private val rowsPerTransaction = cfg.getInt("akka-persistence-jdbc.migration.v4.journal.rows-per-transaction")

  def run(): Unit = {
    val db = SlickDatabase.forConfig(jdbcJournalConfig, journalConfig.slickConfiguration)
    try {
      val migration = for {
        eventsToMigrate <- countEventsToMigrate(db)
        _ = log.info(s"Migrating {} journal events in batches of {}.", eventsToMigrate, rowsPerTransaction)
        migrated <- migrateNextBatch(db, migrated = 0, orderingFrom = 0L)
      } yield {
        log.info("Journal migration complete! {} events were migrated.", migrated)
      }
      Await.result(migration, Duration.Inf)
    } finally {
      db.close()
    }
  }

  private def migrateNextBatch(db: Database, migrated: Int, orderingFrom: Long): Future[Int] = {
    migrateJournalBatch(db, orderingFrom).flatMap {
      case (_, None) =>
        log.debug("done!")
        Future.successful(migrated)
      case (batch, Some(maxHandledOrdering)) =>
        log.debug(s"{} events have been migrated, max(ordering)={}", batch, maxHandledOrdering)
        migrateNextBatch(db, migrated + batch, maxHandledOrdering)
    }
  }

  private def countEventsToMigrate(db: Database) = {
    val query = JournalTable
      .filter(_.serId.isEmpty)
      .length
      .result
    db.run(query)
  }

  private def migrateJournalBatch(db: Database, orderingFrom: Long): Future[(Int, Option[Long])] = {
    val batchUpdate = JournalTable
      .filter(_.serId.isEmpty)
      .filter(_.ordering > orderingFrom)
      .sortBy(_.ordering.asc)
      .take(rowsPerTransaction)
      .result
      .flatMap(rows => {
        val maxHandledOrdering: Option[Long] = if (rows.nonEmpty) Some(rows.map(_.ordering).max) else None
        val updates = rows.map { row =>
          val migration = serializer.deserialize(row).flatMap {
            case (pr, tags, _) =>
              serializer.serialize(pr, tags).map { journalRow =>
                val statement = for {
                  theRow <- JournalTable if theRow.persistenceId === row.persistenceId && theRow.sequenceNumber === row.sequenceNumber
                } yield (theRow.event, theRow.eventManifest, theRow.serId, theRow.serManifest, theRow.writerUuid)
                statement.update(journalRow.event, journalRow.eventManifest, journalRow.serId, journalRow.serManifest, journalRow.writerUuid)
              }
          }
          migration match {
            case Success(update) => update
            case Failure(error) =>
              throw new RuntimeException(s"Migration of event with persistence id ${row.persistenceId} and sequence number ${row.sequenceNumber} failed", error)
          }
        }
        DBIO.seq(updates: _*).map(_ => (updates.size, maxHandledOrdering))
      })

    db.run(batchUpdate)
  }
}

class V4SnapshotMigration(cfg: Config, system: ActorSystem) extends SnapshotTables {
  private val log = Logging(system, classOf[V4SnapshotMigration])

  private val jdbcSnapshotStoreConfig = cfg.getConfig("jdbc-snapshot-store")
  private val snapshotConfig = new SnapshotConfig(jdbcSnapshotStoreConfig)

  if (!snapshotConfig.snapshotTableConfiguration.hasSnapshotColumn) {
    throw new IllegalArgumentException("Snapshot table configuration does not have snapshot column, cannot perform migration.")
  }

  override val profile = SlickDriver.forDriverName(jdbcSnapshotStoreConfig)

  import profile.api._
  import system.dispatcher

  override def snapshotTableCfg = snapshotConfig.snapshotTableConfiguration

  private val serializer = new ByteArraySnapshotSerializer(SerializationExtension(system), false)

  /**
   * The number of rows migrated per transaction.
   */
  private val rowsPerTransaction = cfg.getInt("akka-persistence-jdbc.migration.v4.snapshot.rows-per-transaction")

  def run(): Unit = {
    val db = SlickDatabase.forConfig(jdbcSnapshotStoreConfig, snapshotConfig.slickConfiguration)
    try {
      val migration = for {
        snapshotsToMigrate <- countSnapshotsToMigrate(db)
        _ = log.info("Migrating {} snapshots in batches of {}.", snapshotsToMigrate, rowsPerTransaction)
        migrated <- migrateNextBatch(db, migrated = 0, createdFrom = 0L)
      } yield {
        log.info("Snapshot migration complete! {} snapshots were migrated.", migrated)
      }
      Await.result(migration, Duration.Inf)
    } finally {
      db.close()
    }
  }

  private def countSnapshotsToMigrate(db: Database) = {
    val query = SnapshotTable
      .filter(_.serId.isEmpty)
      .length
      .result
    db.run(query)
  }

  private def migrateNextBatch(db: Database, migrated: Long, createdFrom: Long): Future[Long] = {
    migrateSnapshotBatch(db, createdFrom).flatMap {
      case (_, None) =>
        log.debug("done!")
        Future.successful(migrated)
      case (batch, Some(maxHandledCreated)) =>
        log.debug("{} snapshots have been migrated", batch)
        migrateNextBatch(db, migrated + batch, maxHandledCreated)
    }
  }

  private def migrateSnapshotBatch(db: Database, createdFrom: Long): Future[(Int, Option[Long])] = {
    val batchUpdate = SnapshotTable
      .filter(_.serId.isEmpty)
      .filter(_.created > createdFrom)
      .sortBy(_.created.asc)
      .take(rowsPerTransaction)
      .result
      .flatMap(rows => {
        val maxHandledCreated: Option[Long] = if (rows.nonEmpty) Some(rows.map(_.created).max) else None
        val updates = rows.map { row =>
          val migration = serializer.deserialize(row).flatMap {
            case (metadata, snapshot) =>
              serializer.serialize(metadata, snapshot).map { snapshotRow =>
                val statement = for {
                  theRow <- SnapshotTable if theRow.persistenceId === row.persistenceId && theRow.sequenceNumber === row.sequenceNumber
                } yield (theRow.snapshotData, theRow.serId, theRow.serManifest)
                statement.update(snapshotRow.snapshotData, snapshotRow.serId, snapshotRow.serManifest)
              }
          }
          migration match {
            case Success(update) => update
            case Failure(error) =>
              throw new RuntimeException(s"Migration of snapshot with persistence id ${row.persistenceId} and sequence number ${row.sequenceNumber} failed", error)
          }
        }
        DBIO.seq(updates: _*).map(_ => (updates.size, maxHandledCreated))
      })

    db.run(batchUpdate)
  }
}

object V4Migration extends App {

  val system = ActorSystem()
  try {
    val config = system.settings.config
    new V4JournalMigration(config, system).run()
    new V4SnapshotMigration(config, system).run()
  } finally {
    system.terminate()
  }

}
