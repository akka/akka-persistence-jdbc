/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.migrator

import akka.actor.ActorSystem
import akka.persistence.SnapshotMetadata
import akka.persistence.jdbc.config.SnapshotConfig
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.snapshot.dao.DefaultSnapshotDao
import akka.persistence.jdbc.snapshot.dao.legacy.{ ByteArraySnapshotSerializer, SnapshotQueries }
import akka.persistence.jdbc.snapshot.dao.legacy.SnapshotTables.SnapshotRow
import akka.serialization.SerializationExtension
import com.typesafe.config.Config
import slick.basic.DatabaseConfig
import slick.jdbc
import slick.jdbc.JdbcProfile

import scala.concurrent.Future
import scala.util.{ Failure, Success }

/**
 * This will help migrate the legacy snapshot data onto the new snapshot schema with the
 * appropriate serialization
 *
 * @param config the application config
 * @param system the actor system
 */
case class LegacySnapshotDataMigrator(config: Config)(implicit system: ActorSystem) {

  import system.dispatcher

  // get the Jdbc Profile
  protected val profile: JdbcProfile = DatabaseConfig.forConfig[JdbcProfile]("slick").profile

  import profile.api._

  private val snapshotConfig = new SnapshotConfig(config.getConfig("jdbc-snapshot-store"))

  val snapshotdb: jdbc.JdbcBackend.Database =
    SlickExtension(system).database(config.getConfig("jdbc-snapshot-store")).database

  private val serialization = SerializationExtension(system)
  private val queries = new SnapshotQueries(profile, snapshotConfig.legacySnapshotTableConfiguration)
  private val serializer = new ByteArraySnapshotSerializer(serialization)

  // get the instance if the default snapshot dao
  private val defaultSnapshotDao: DefaultSnapshotDao =
    new DefaultSnapshotDao(snapshotdb, profile, snapshotConfig, serialization)

  private def toSnapshotData(row: SnapshotRow): (SnapshotMetadata, Any) =
    serializer.deserialize(row) match {
      case Success(deserialized) => deserialized
      case Failure(cause)        => throw cause
    }

  /**
   * migrate all the legacy snapshot schema data into the new snapshot schema
   */
  def migrateAll(): Future[Seq[Future[Unit]]] = {
    for {
      rows <- snapshotdb.run(queries.SnapshotTable.sortBy(_.sequenceNumber.desc).result)
    } yield rows.map(toSnapshotData).map { case (metadata, value) =>
      defaultSnapshotDao.save(metadata, value)
    }
  }

  def migrate(offset: Int, limit: Int): Future[Seq[Future[Unit]]] = {
    for {
      rows <- snapshotdb.run(queries.SnapshotTable.sortBy(_.sequenceNumber.desc).drop(offset).take(limit).result)
    } yield rows.map(toSnapshotData).map { case (metadata, value) =>
      defaultSnapshotDao.save(metadata, value)
    }
  }

  def migrateLatest(): Future[Option[Future[Unit]]] = {
    for {
      rows <- snapshotdb.run(queries.SnapshotTable.sortBy(_.sequenceNumber.desc).take(1).result)
    } yield rows.headOption.map(toSnapshotData).map { case (metadata, value) =>
      defaultSnapshotDao.save(metadata, value)
    }
  }
}
