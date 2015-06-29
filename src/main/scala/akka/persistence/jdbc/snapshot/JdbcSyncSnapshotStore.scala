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

trait JdbcSyncSnapshotStore extends SnapshotStore with ActorLogging with ActorConfig with JdbcStatements {
  implicit val system = context.system
  implicit val executionContext = context.system.dispatcher

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    log.debug("loading for persistenceId: {}, criteria: {}", persistenceId, criteria)
    Future[Option[SelectedSnapshot]] {
      selectSnapshotFor(persistenceId, criteria)
    }
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = Future {
    log.debug("Saving metadata: {}, snapshot: {}", metadata, snapshot)
    writeSnapshot(metadata, Snapshot(snapshot))
  }

  override def saved(metadata: SnapshotMetadata): Unit =
    log.debug("Saved: {}", metadata)

  override def delete(metadata: SnapshotMetadata): Unit = {
    log.debug("Deleting: {}", metadata)
    deleteSnapshot(metadata)
  }

  override def delete(persistenceId: String, criteria: SnapshotSelectionCriteria): Unit = {
    log.debug("Deleting for persistenceId: {} and criteria: {}", persistenceId, criteria)
    deleteSnapshots(persistenceId, criteria)
  }
}
