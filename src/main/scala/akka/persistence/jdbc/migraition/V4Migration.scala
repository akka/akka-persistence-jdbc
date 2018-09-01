package akka.persistence.jdbc.migraition

import akka.actor.ActorSystem
import akka.persistence.jdbc.config.{ JournalConfig, JournalTableConfiguration, SnapshotConfig }
import akka.persistence.jdbc.journal.dao.{ ByteArrayJournalSerializer, JournalTables }
import akka.persistence.jdbc.snapshot.dao.{ ByteArraySnapshotSerializer, SnapshotTables }
import akka.persistence.jdbc.util.{ SlickDatabase, SlickDriver }
import akka.serialization.SerializationExtension
import com.typesafe.config.Config

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

class V4JournalMigration(config: Config, system: ActorSystem) extends JournalTables {

  private val journalConfig = new JournalConfig(config)

  if (!journalConfig.journalTableConfiguration.hasMessageColumn) {
    throw new IllegalArgumentException("Journal table configuration does not have message column, cannot perform migration.")
  }

  override val profile = SlickDriver.forDriverName(config)

  import profile.api._
  import system.dispatcher

  override def journalTableCfg: JournalTableConfiguration = journalConfig.journalTableConfiguration

  private val serializer = new ByteArrayJournalSerializer(SerializationExtension(system), journalConfig.pluginConfig.tagSeparator,
    false)

  /**
   * The number of rows migrated per transaction.
   */
  private val RowsPerTransaction = 100

  def run(): Unit = {
    val db = SlickDatabase.forConfig(config, journalConfig.slickConfiguration)
    try {
      println(s"Migrating journal events, each . indicates $RowsPerTransaction rows migrated.")
      val migration = migrateNextBatch(db, 0)
      val migrated = Await.result(migration, Duration.Inf)
      println(s"Journal migration complete! $migrated events were migrated.")
    } finally {
      db.close()
    }
  }

  private def migrateNextBatch(db: Database, migrated: Long): Future[Long] = {
    migrateJournalBatch(db).flatMap {
      case 0 =>
        println(" done!")
        Future.successful(migrated)
      case batch =>
        print(".")
        migrateNextBatch(db, migrated + batch)
    }
  }

  private def migrateJournalBatch(db: Database): Future[Int] = {
    val batchUpdate = JournalTable
      .filter(_.event.isEmpty)
      .take(RowsPerTransaction)
      .result
      .flatMap(rows => {
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
        DBIO.seq(updates: _*).map(_ => updates.size)
      })

    db.run(batchUpdate)
  }
}

class V4SnapshotMigration(config: Config, system: ActorSystem) extends SnapshotTables {

  private val snapshotConfig = new SnapshotConfig(config)

  if (!snapshotConfig.snapshotTableConfiguration.hasSnapshotColumn) {
    throw new IllegalArgumentException("Snapshot table configuration does not have snapshot column, cannot perform migration.")
  }

  override val profile = SlickDriver.forDriverName(config)

  import profile.api._
  import system.dispatcher

  override def snapshotTableCfg = snapshotConfig.snapshotTableConfiguration

  private val serializer = new ByteArraySnapshotSerializer(SerializationExtension(system), false)

  /**
   * The number of rows migrated per transaction.
   */
  private val RowsPerTransaction = 100

  def run(): Unit = {
    val db = SlickDatabase.forConfig(config, snapshotConfig.slickConfiguration)
    try {
      println(s"Migrating snapshots, each . indicates $RowsPerTransaction rows migrated.")
      val migration = migrateNextBatch(db, 0)
      val migrated = Await.result(migration, Duration.Inf)
      println(s"Snapshot migration complete! $migrated snapshots were migrated.")
    } finally {
      db.close()
    }
  }

  private def migrateNextBatch(db: Database, migrated: Long): Future[Long] = {
    migrateSnapshotBatch(db).flatMap {
      case 0 =>
        println(" done!")
        Future.successful(migrated)
      case batch =>
        print(".")
        migrateNextBatch(db, migrated + batch)
    }
  }

  private def migrateSnapshotBatch(db: Database): Future[Int] = {
    val batchUpdate = SnapshotTable
      .filter(_.snapshotData.isEmpty)
      .take(RowsPerTransaction)
      .result
      .flatMap(rows => {
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
        DBIO.seq(updates: _*).map(_ => updates.size)
      })

    db.run(batchUpdate)
  }
}

object V4Migration extends App {

  val system = ActorSystem()
  try {
    val config = system.settings.config
    new V4JournalMigration(config.getConfig("jdbc-journal"), system).run()
    new V4SnapshotMigration(config.getConfig("jdbc-snapshot-store"), system).run()
  } finally {
    system.terminate()
  }

}
