package akka.persistence.jdbc.tools

import akka.actor.ActorSystem
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.journal.dao.DefaultJournalDao
import akka.persistence.jdbc.query.dao.legacy.ByteArrayReadJournalDao
import akka.persistence.jdbc.snapshot.dao.DefaultSnapshotDao
import akka.persistence.jdbc.snapshot.dao.legacy.ByteArraySnapshotDao
import akka.serialization.SerializationExtension
import slick.basic.DatabaseConfig
import slick.jdbc
import slick.jdbc.{ JdbcBackend, JdbcProfile }

/**
 * Wrapper class defining the various DAOs that are needed to run journal data migration
 *
 * @param storesConfig the stores config
 * @param system the actor system
 */
final case class MigrationDaos(storesConfig: StoresConfig)(implicit system: ActorSystem) {
  import system.dispatcher

  // get an instance of the database and the Jdbc profile
  val profile: JdbcProfile = DatabaseConfig.forConfig[JdbcProfile]("slick").profile

  val journaldb: JdbcBackend.Database =
    SlickExtension(system).database(storesConfig.config.getConfig("jdbc-read-journal")).database
  val snapshotdb: jdbc.JdbcBackend.Database =
    SlickExtension(system).database(storesConfig.config.getConfig("jdbc-snapshot-store")).database

  // get an instance of the legacy read journal dao
  val legacyReadJournalDao: ByteArrayReadJournalDao =
    new ByteArrayReadJournalDao(journaldb, profile, storesConfig.readJournalConfig, SerializationExtension(system))

  // get an instance of the default journal dao
  val defaultJournalDao: DefaultJournalDao =
    new DefaultJournalDao(journaldb, profile, storesConfig.journalConfig, SerializationExtension(system))

  // get the instance of the legacy snapshot dao
  val legacySnapshotDao: ByteArraySnapshotDao =
    new ByteArraySnapshotDao(snapshotdb, profile, storesConfig.snapshotConfig, SerializationExtension(system))

  // get the instance if the default snapshot dao
  val defaultSnapshotDao: DefaultSnapshotDao =
    new DefaultSnapshotDao(snapshotdb, profile, storesConfig.snapshotConfig, SerializationExtension(system))

}
