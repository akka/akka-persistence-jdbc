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

package akka.persistence.jdbc
package journal.dao

import akka.actor.ActorRef
import akka.persistence.PersistentRepr
import akka.persistence.jdbc.serialization.FlowPersistentReprSerializer
import akka.serialization.{ Serialization, Serializers }

import scala.collection.immutable._
import scala.util.{ Success, Try }

class ByteArrayJournalSerializer(serialization: Serialization, separator: String) extends FlowPersistentReprSerializer[JournalRow] {
  override def serialize(persistentRepr: PersistentRepr, tags: Set[String]): Try[JournalRow] = {
    val payload = persistentRepr.payload.asInstanceOf[AnyRef]
    val tryEvent = serialization.serialize(payload)

    for {
      message <- serialization.serialize(persistentRepr)
      event <- tryEvent
    } yield {
      val serializer = serialization.findSerializerFor(payload)
      val serManifest = Serializers.manifestFor(serializer, payload)
      JournalRow(
        Long.MinValue,
        persistentRepr.deleted,
        persistentRepr.persistenceId,
        persistentRepr.sequenceNr,
        encodeTags(tags, separator),
        event,
        persistentRepr.manifest,
        serializer.identifier,
        serManifest,
        persistentRepr.writerUuid)
    }
  }

  override def deserialize(journalRow: JournalRow): Try[(PersistentRepr, Set[String], Long)] = {
    serialization.deserialize(
      journalRow.event,
      journalRow.serId,
      journalRow.serManifest).map { payload =>
        (PersistentRepr(
          payload,
          journalRow.sequenceNumber,
          journalRow.persistenceId,
          journalRow.eventManifest,
          journalRow.deleted,
          ActorRef.noSender,
          journalRow.writerUuid), decodeTags(journalRow.tags, separator), journalRow.ordering)
      }
  }
}

class LegacyByteArrayJournalSerializer(serialization: Serialization, separator: String, writeMessageColumn: Boolean) extends FlowPersistentReprSerializer[LegacyJournalRow] {
  override def serialize(persistentRepr: PersistentRepr, tags: Set[String]): Try[LegacyJournalRow] = {

    val tryMessageColumn = if (writeMessageColumn) {
      serialization.serialize(persistentRepr).map(Some.apply)
    } else {
      Success(None)
    }
    val payload = persistentRepr.payload.asInstanceOf[AnyRef]
    val tryEvent = serialization.serialize(payload)

    for {
      maybeMessage <- tryMessageColumn
      event <- tryEvent
    } yield {
      val serializer = serialization.findSerializerFor(payload)
      val serManifest = Serializers.manifestFor(serializer, payload)
      LegacyJournalRow(
        Long.MinValue,
        persistentRepr.deleted,
        persistentRepr.persistenceId,
        persistentRepr.sequenceNr,
        maybeMessage,
        encodeTags(tags, separator),
        Some(event),
        Some(persistentRepr.manifest),
        Some(serializer.identifier),
        Some(serManifest),
        Some(persistentRepr.writerUuid))
    }
  }

  override def deserialize(journalRow: LegacyJournalRow): Try[(PersistentRepr, Set[String], Long)] = {
    serialization.deserialize(
      journalRow.event.get,
      journalRow.serId.get,
      journalRow.serManifest.getOrElse("")).map { payload =>
        (PersistentRepr(
          payload,
          journalRow.sequenceNumber,
          journalRow.persistenceId,
          journalRow.eventManifest.getOrElse(PersistentRepr.Undefined),
          journalRow.deleted,
          ActorRef.noSender,
          journalRow.writerUuid.getOrElse(PersistentRepr.Undefined)), decodeTags(journalRow.tags, separator), journalRow.ordering)
      }
  }
}
