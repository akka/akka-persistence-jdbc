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
import akka.serialization.{Serialization, SerializerWithStringManifest, Serializers}

import scala.collection.immutable._
import scala.util.{Failure, Success, Try}

class ByteArrayJournalSerializer(serialization: Serialization, separator: String, writeMessageColumn: Boolean) extends FlowPersistentReprSerializer[JournalRow] {
  override def serialize(persistentRepr: PersistentRepr, tags: Set[String]): Try[JournalRow] = {

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
      JournalRow(
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

  override def deserialize(journalRow: JournalRow): Try[(PersistentRepr, Set[String], Long)] = {
    if (journalRow.event.isDefined && journalRow.serId.isDefined) {
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
    } else if (journalRow.message.isDefined) {
      serialization.deserialize(journalRow.message.get, classOf[PersistentRepr])
        .map((_, decodeTags(journalRow.tags, separator), journalRow.ordering))
    } else {
      Failure(new RuntimeException("Row does not define an event or message"))
    }
  }
}
