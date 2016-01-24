/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.jdbc.snapshot

import akka.persistence.jdbc.dao.SnapshotDao
import akka.persistence.jdbc.dao.SnapshotDao.SnapshotData
import akka.persistence.jdbc.serialization.SerializationProxy
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{ SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria }
import akka.stream.Materializer

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

object SlickSnapshotStore {
  def mapToSelectedSnapshot(data: SnapshotData, serializationProxy: SerializationProxy): Try[SelectedSnapshot] = for {
    snapshot ← serializationProxy.deserialize(data.snapshot, classOf[Snapshot])
  } yield SelectedSnapshot(SnapshotMetadata(data.persistenceId, data.sequenceNumber, data.created), snapshot.data)
}

trait SlickSnapshotStore extends SnapshotStore {
  import SlickSnapshotStore._

  def snapshotDao: SnapshotDao

  implicit def mat: Materializer

  implicit def ec: ExecutionContext

  def serializationProxy: SerializationProxy

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    val result = criteria match {
      case SnapshotSelectionCriteria(Long.MaxValue, Long.MaxValue, _, _) ⇒
        snapshotDao.snapshotForMaxSequenceNr(persistenceId)
      case SnapshotSelectionCriteria(Long.MaxValue, maxTimestamp, _, _) ⇒
        snapshotDao.snapshotForMaxTimestamp(persistenceId, maxTimestamp)
      case SnapshotSelectionCriteria(maxSequenceNr, Long.MaxValue, _, _) ⇒
        snapshotDao.snapshotForMaxSequenceNr(persistenceId, maxSequenceNr)
      case SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp, _, _) ⇒
        snapshotDao.snapshotForMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp)
      case _ ⇒ Future.successful(None)
    }

    for {
      snapshotDataOption ← result
      selectedSnapshot = for {
        snapshotData: SnapshotData ← snapshotDataOption
        selectedSnapshot: SelectedSnapshot ← mapToSelectedSnapshot(snapshotData, serializationProxy).toOption
      } yield selectedSnapshot
    } yield selectedSnapshot
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = for {
    snapshot ← Future.fromTry(serializationProxy.serialize(Snapshot(snapshot)))
    _ ← snapshotDao.save(metadata.persistenceId, metadata.sequenceNr, metadata.timestamp, snapshot)
  } yield ()

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = for {
    _ ← snapshotDao.delete(metadata.persistenceId, metadata.sequenceNr)
  } yield ()

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = criteria match {
    case SnapshotSelectionCriteria(Long.MaxValue, Long.MaxValue, _, _) ⇒
      snapshotDao.deleteAllSnapshots(persistenceId)
    case SnapshotSelectionCriteria(Long.MaxValue, maxTimestamp, _, _) ⇒
      snapshotDao.deleteUpToMaxTimestamp(persistenceId, maxTimestamp)
    case SnapshotSelectionCriteria(maxSequenceNr, Long.MaxValue, _, _) ⇒
      snapshotDao.deleteUpToMaxSequenceNr(persistenceId, maxSequenceNr)
    case SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp, _, _) ⇒
      snapshotDao.deleteUpToMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp)
    case _ ⇒ Future.successful(())
  }
}
