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

import akka.actor.ActorLogging
import akka.persistence.jdbc.common.ActorConfig
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{ SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria }

import scala.concurrent.Future
import scala.util.Try

trait JdbcSyncSnapshotStore extends SnapshotStore with ActorLogging with ActorConfig with JdbcStatements {
  implicit val system = context.system

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] =
    Future.fromTry(Try(selectSnapshotFor(persistenceId, criteria)))

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] =
    Future.fromTry(Try(writeSnapshot(metadata, Snapshot(snapshot))))

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] =
    Future.fromTry(Try(deleteSnapshot(metadata)))

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] =
    Future.fromTry(Try(deleteSnapshots(persistenceId, criteria)))
}
