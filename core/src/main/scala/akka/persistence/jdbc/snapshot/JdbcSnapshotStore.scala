/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2025 Lightbend Inc. <https://akka.io>
 */

package akka.persistence.jdbc.snapshot

import akka.actor.ActorSystem
import akka.persistence.jdbc.config.SnapshotConfig
import akka.persistence.jdbc.snapshot.dao.{ SnapshotDao, SnapshotDaoInstantiation }
import akka.persistence.jdbc.db.{ SlickDatabase, SlickExtension }
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{ SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria }
import akka.stream.{ Materializer, SystemMaterializer }
import com.typesafe.config.Config
import slick.jdbc.JdbcBackend._

import scala.concurrent.{ ExecutionContext, Future }

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
  implicit val mat: Materializer = SystemMaterializer(system).materializer
  val snapshotConfig = new SnapshotConfig(config)

  val slickDb: SlickDatabase = SlickExtension(system).database(config)
  def db: Database = slickDb.database

  val snapshotDao: SnapshotDao = SnapshotDaoInstantiation.snapshotDao(snapshotConfig, slickDb)

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
      case null => Future.successful(None)
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
      case null => Future.successful(())
    }

  override def postStop(): Unit = {
    if (slickDb.allowShutdown) {
      // Since a (new) db is created when this actor (re)starts, we must close it when the actor stops
      db.close()
    }
    super.postStop()
  }
}
