/*
 * Copyright 2015 Dennis Vriend
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

import akka.actor.{ ActorLogging, ActorSystem }
import akka.persistence.jdbc.common.ActorConfig
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{ SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

trait JdbcSyncSnapshotStore extends SnapshotStore with ActorLogging with ActorConfig with JdbcStatements {
  def system: ActorSystem
  implicit def executionContext: ExecutionContext

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] =
    Future.fromTry(Try(selectSnapshotFor(persistenceId, criteria)))

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] =
    Future.fromTry(Try(writeSnapshot(metadata, Snapshot(snapshot))))

  override def saved(metadata: SnapshotMetadata): Unit = ()

  override def delete(metadata: SnapshotMetadata): Unit =
    deleteSnapshot(metadata)

  override def delete(persistenceId: String, criteria: SnapshotSelectionCriteria): Unit =
    deleteSnapshots(persistenceId, criteria)
}
