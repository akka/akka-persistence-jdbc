/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.snapshot.dao.legacy

import akka.persistence.SnapshotMetadata
import akka.persistence.jdbc.serialization.SnapshotSerializer
import akka.persistence.jdbc.snapshot.dao.legacy.SnapshotTables.SnapshotRow
import akka.persistence.serialization.Snapshot
import akka.serialization.Serialization

import scala.util.Try

class ByteArraySnapshotSerializer(serialization: Serialization) extends SnapshotSerializer[SnapshotRow] {
  def serialize(metadata: SnapshotMetadata, snapshot: Any): Try[SnapshotRow] = {
    serialization
      .serialize(Snapshot(snapshot))
      .map(SnapshotRow(metadata.persistenceId, metadata.sequenceNr, metadata.timestamp, _))
  }

  def deserialize(snapshotRow: SnapshotRow): Try[(SnapshotMetadata, Any)] = {
    serialization
      .deserialize(snapshotRow.snapshot, classOf[Snapshot])
      .map(snapshot => {
        val snapshotMetadata =
          SnapshotMetadata(snapshotRow.persistenceId, snapshotRow.sequenceNumber, snapshotRow.created)
        (snapshotMetadata, snapshot.data)
      })
  }
}
