package akka.persistence.jdbc.snapshot

import akka.actor.ActorLogging
import akka.persistence.{Persistence, SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.persistence.jdbc.common.{ScalikeConnection, ActorConfig}
import akka.persistence.snapshot.SnapshotStore
import akka.serialization.SerializationExtension

import scala.concurrent.Future

class JdbcSyncSnapshotStore extends SnapshotStore with ActorLogging with ActorConfig with ScalikeConnection {
  implicit val system = context.system
  val extension = Persistence(context.system)
  val serialization = SerializationExtension(context.system)
  implicit val executionContext = context.system.dispatcher

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = ???

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = ???

  override def saved(metadata: SnapshotMetadata): Unit = ???

  override def delete(metadata: SnapshotMetadata): Unit = ???

  override def delete(persistenceId: String, criteria: SnapshotSelectionCriteria): Unit = ???
}
