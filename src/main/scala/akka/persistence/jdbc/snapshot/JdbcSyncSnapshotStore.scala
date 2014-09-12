package akka.persistence.jdbc.snapshot

import akka.actor.ActorLogging
import akka.persistence.jdbc.common.ActorConfig
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SnapshotMetadata, Persistence, SelectedSnapshot, SnapshotSelectionCriteria}
import akka.serialization.SerializationExtension

import scala.concurrent.Future

trait JdbcSyncSnapshotStore extends SnapshotStore with ActorLogging with ActorConfig with JdbcStatements {
  implicit val system = context.system
  val extension = Persistence(context.system)
  val serialization = SerializationExtension(context.system)
  implicit val executionContext = context.system.dispatcher

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    log.debug("loading for persistenceId: {}, criteria: {}", persistenceId, criteria)
    Future[Option[SelectedSnapshot]] {
      selectSnapshotsFor(persistenceId, criteria).headOption
    }
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = Future {
    log.debug("Saving metadata: {}, snapshot: {}", metadata, snapshot)
    writeSnapshot(metadata, Snapshot(snapshot))
  }

  override def saved(metadata: SnapshotMetadata): Unit = log.debug("Saved: {}", metadata)

  override def delete(metadata: SnapshotMetadata): Unit = {
    log.debug("Deleting: {}", metadata)
    deleteSnapshot(metadata)
  }

  override def delete(persistenceId: String, criteria: SnapshotSelectionCriteria): Unit = {
    log.debug("Deleting for persistenceId: {} and criteria: {}", persistenceId, criteria)
    selectSnapshotsFor(persistenceId, criteria).foreach { selectedSnapshot =>
      deleteSnapshot(selectedSnapshot.metadata)
    }
  }
}
