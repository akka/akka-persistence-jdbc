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

package akka.persistence.jdbc.snapshot.dao

import akka.persistence.SnapshotMetadata
import akka.persistence.jdbc.serialization.SnapshotSerializer
import akka.persistence.jdbc.snapshot.dao.SnapshotTables.SnapshotRow
import akka.persistence.serialization.Snapshot
import akka.serialization.{ Serialization, SerializerWithStringManifest }

import scala.util.{ Failure, Success, Try }

class ByteArraySnapshotSerializer(serialization: Serialization, writeSnapshotColumn: Boolean) extends SnapshotSerializer[SnapshotRow] {

  def serialize(metadata: SnapshotMetadata, snapshot: Any): Try[SnapshotRow] = {
    val trySnapshotColumn = if (writeSnapshotColumn) {
      serialization.serialize(Snapshot(snapshot)).map(Some.apply)
    } else {
      Success(None)
    }
    val snapshotRef = snapshot.asInstanceOf[AnyRef]
    val trySnapshotData = serialization.serialize(snapshotRef)

    for {
      maybeSnapshot <- trySnapshotColumn
      snapshotData <- trySnapshotData
    } yield {
      val serializer = serialization.findSerializerFor(snapshotRef)
      val serManifest = serializer match {
        case stringManifest: SerializerWithStringManifest =>
          stringManifest.manifest(snapshotRef)
        case _ if serializer.includeManifest =>
          snapshotRef.getClass.getName
        case _ => ""
      }
      SnapshotRow(
        metadata.persistenceId,
        metadata.sequenceNr,
        metadata.timestamp,
        maybeSnapshot,
        Some(snapshotData),
        Some(serializer.identifier),
        Some(serManifest))
    }
  }

  def deserialize(snapshotRow: SnapshotRow): Try[(SnapshotMetadata, Any)] = {
    val metadata = SnapshotMetadata(snapshotRow.persistenceId, snapshotRow.sequenceNumber, snapshotRow.created)
    if (snapshotRow.snapshotData.isDefined && snapshotRow.serId.isDefined) {
      serialization.deserialize(
        snapshotRow.snapshotData.get,
        snapshotRow.serId.get,
        snapshotRow.serManifest.getOrElse("")).map(snapshot => (metadata, snapshot))
    } else if (snapshotRow.snapshot.isDefined) {
      serialization
        .deserialize(snapshotRow.snapshot.get, classOf[Snapshot])
        .map(snapshot => (metadata, snapshot.data))
    } else {
      Failure(new RuntimeException("Row does not define an event or message"))
    }
  }

}
