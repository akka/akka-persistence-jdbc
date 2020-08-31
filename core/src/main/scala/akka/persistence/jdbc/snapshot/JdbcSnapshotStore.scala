/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.snapshot

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.persistence.jdbc.config.SnapshotConfig
import akka.persistence.jdbc.snapshot.dao.SnapshotDao
import akka.persistence.jdbc.db.{ SlickDatabase, SlickExtension }
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{ SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria }
import akka.serialization.{ Serialization, SerializationExtension }
import akka.stream.{ ActorMaterializer, Materializer }
import com.typesafe.config.Config
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcBackend._

import scala.collection.immutable._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object JdbcSnapshotStore {
  def toSelectedSnapshot(tupled: (SnapshotMetadata, Any)): SelectedSnapshot =
    tupled match {
      case (meta: SnapshotMetadata, snapshot: Any) => SelectedSnapshot(meta, snapshot)
    }
}

class JdbcSnapshotStore(config: Config) extends SnapshotStore {
  import JdbcSnapshotStore._

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val system: ActorSystem = context.system
  implicit val mat: Materializer = ActorMaterializer()
  val snapshotConfig = new SnapshotConfig(config)

  val slickDb: SlickDatabase = SlickExtension(system).database(config)
  def db: Database = slickDb.database

  val snapshotDao: SnapshotDao = {
    val fqcn = snapshotConfig.pluginConfig.dao
    val profile: JdbcProfile = slickDb.profile
    val args = Seq(
      (classOf[Database], db),
      (classOf[JdbcProfile], profile),
      (classOf[SnapshotConfig], snapshotConfig),
      (classOf[Serialization], SerializationExtension(system)),
      (classOf[ExecutionContext], ec),
      (classOf[Materializer], mat))
    system.asInstanceOf[ExtendedActorSystem].dynamicAccess.createInstanceFor[SnapshotDao](fqcn, args) match {
      case Success(dao)   => dao
      case Failure(cause) => throw cause
    }
  }

  override def loadAsync(
      persistenceId: String,
      criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    val result = criteria match {
      case SnapshotSelectionCriteria(Long.MaxValue, Long.MaxValue, _, _) =>
        snapshotDao.latestSnapshot(persistenceId)
      case SnapshotSelectionCriteria(Long.MaxValue, maxTimestamp, _, _) =>
        snapshotDao.snapshotForMaxTimestamp(persistenceId, maxTimestamp)
      case SnapshotSelectionCriteria(maxSequenceNr, Long.MaxValue, _, _) =>
        snapshotDao.snapshotForMaxSequenceNr(persistenceId, maxSequenceNr)
      case SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp, _, _) =>
        snapshotDao.snapshotForMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp)
      case _ => Future.successful(None)
    }

    result.map(_.map(toSelectedSnapshot))
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] =
    snapshotDao.save(metadata, snapshot)

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] =
    for {
      _ <- snapshotDao.delete(metadata.persistenceId, metadata.sequenceNr)
    } yield ()

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] =
    criteria match {
      case SnapshotSelectionCriteria(Long.MaxValue, Long.MaxValue, _, _) =>
        snapshotDao.deleteAllSnapshots(persistenceId)
      case SnapshotSelectionCriteria(Long.MaxValue, maxTimestamp, _, _) =>
        snapshotDao.deleteUpToMaxTimestamp(persistenceId, maxTimestamp)
      case SnapshotSelectionCriteria(maxSequenceNr, Long.MaxValue, _, _) =>
        snapshotDao.deleteUpToMaxSequenceNr(persistenceId, maxSequenceNr)
      case SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp, _, _) =>
        snapshotDao.deleteUpToMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp)
      case _ => Future.successful(())
    }

  override def postStop(): Unit = {
    if (slickDb.allowShutdown) {
      // Since a (new) db is created when this actor (re)starts, we must close it when the actor stops
      db.close()
    }
    super.postStop()
  }
}
