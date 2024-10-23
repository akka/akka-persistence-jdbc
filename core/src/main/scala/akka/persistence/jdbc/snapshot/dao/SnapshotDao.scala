/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.snapshot.dao

import akka.persistence.SnapshotMetadata

import scala.concurrent.Future

trait SnapshotDao {
  def deleteAllSnapshots(persistenceId: String): Future[Unit]

  def deleteUpToMaxSequenceNr(persistenceId: String, maxSequenceNr: Long): Future[Unit]

  def deleteUpToMaxTimestamp(persistenceId: String, maxTimestamp: Long): Future[Unit]

  def deleteUpToMaxSequenceNrAndMaxTimestamp(
      persistenceId: String,
      maxSequenceNr: Long,
      maxTimestamp: Long): Future[Unit]

  def latestSnapshot(persistenceId: String): Future[Option[(SnapshotMetadata, Any)]]

  def snapshotForMaxTimestamp(persistenceId: String, timestamp: Long): Future[Option[(SnapshotMetadata, Any)]]

  def snapshotForMaxSequenceNr(persistenceId: String, sequenceNr: Long): Future[Option[(SnapshotMetadata, Any)]]

  def snapshotForMaxSequenceNrAndMaxTimestamp(
      persistenceId: String,
      sequenceNr: Long,
      timestamp: Long): Future[Option[(SnapshotMetadata, Any)]]

  def delete(persistenceId: String, sequenceNr: Long): Future[Unit]

  def save(snapshotMetadata: SnapshotMetadata, snapshot: Any): Future[Unit]
}
