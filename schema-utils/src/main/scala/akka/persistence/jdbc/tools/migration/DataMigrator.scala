/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.tools.migration

import akka.actor.ActorSystem
import akka.persistence.jdbc.config.{ JournalConfig, ReadJournalConfig, SnapshotConfig }
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.journal.dao.DefaultJournalDao
import akka.persistence.jdbc.query.dao.legacy.ByteArrayReadJournalDao
import akka.persistence.jdbc.snapshot.dao.legacy.ByteArraySnapshotDao
import akka.persistence.jdbc.snapshot.dao.DefaultSnapshotDao
import akka.serialization.SerializationExtension
import com.typesafe.config.Config
import slick.basic.DatabaseConfig
import slick.jdbc.{ JdbcBackend, JdbcProfile }
import slick.jdbc

/**
 * this class will be implemented by both the journal and the snapshot migration class
 *
 * @param config the application config
 * @param system the actor system
 */
abstract class DataMigrator(config: Config)(implicit system: ActorSystem) {

  import system.dispatcher

  // get an instance of the database and the Jdbc profile
  protected val profile: JdbcProfile = DatabaseConfig.forConfig[JdbcProfile]("slick").profile

  // get the various configuration
  protected val journalConfig = new JournalConfig(config.getConfig("jdbc-journal"))
  protected val readJournalConfig = new ReadJournalConfig(config.getConfig("jdbc-read-journal"))

  protected val journaldb: JdbcBackend.Database =
    SlickExtension(system).database(config.getConfig("jdbc-read-journal")).database
  protected val snapshotdb: jdbc.JdbcBackend.Database =
    SlickExtension(system).database(config.getConfig("jdbc-snapshot-store")).database

  // get an instance of the legacy read journal dao
  protected val legacyReadJournalDao: ByteArrayReadJournalDao =
    new ByteArrayReadJournalDao(journaldb, profile, readJournalConfig, SerializationExtension(system))

  // get an instance of the default journal dao
  protected val defaultJournalDao: DefaultJournalDao =
    new DefaultJournalDao(journaldb, profile, journalConfig, SerializationExtension(system))

  protected val snapshotConfig = new SnapshotConfig(config.getConfig("jdbc-snapshot-store"))

  // get the instance of the legacy snapshot dao
  val legacySnapshotDao: ByteArraySnapshotDao =
    new ByteArraySnapshotDao(snapshotdb, profile, snapshotConfig, SerializationExtension(system))

  // get the instance if the default snapshot dao
  val defaultSnapshotDao: DefaultSnapshotDao =
    new DefaultSnapshotDao(snapshotdb, profile, snapshotConfig, SerializationExtension(system))
}
