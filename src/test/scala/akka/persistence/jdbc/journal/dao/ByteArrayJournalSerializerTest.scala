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

import java.util.UUID

import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.serialization.SerializationExtension
import org.scalatest.OptionValues

import scala.collection.immutable._

class ByteArrayJournalSerializerTest extends SharedActorSystemTestSpec() with OptionValues {

  val writerUuid = UUID.randomUUID().toString

  it should "serialize a serializable message and indicate whether or not the serialization succeeded" in {
    val serializer = new ByteArrayJournalSerializer(serialization, ",", false)
    val result = serializer.serialize(Seq(AtomicWrite(PersistentRepr("foo", 1L, "id", "event-manifest", writerUuid = writerUuid))))
    result should have size 1
    result.head should be a 'success

    val row = result.head.get.head
    row.message shouldBe None
    row.event shouldBe defined
    row.sequenceNumber shouldBe 1L
    row.persistenceId shouldBe "id"
    row.eventManifest shouldBe Some("event-manifest")
    row.writerUuid shouldBe Some(writerUuid)
    // 1 is the Java serializer
    row.serId shouldBe Some(1)
  }

  it should "serialize a serializable message and also write the message column when configured" in {
    val serializer = new ByteArrayJournalSerializer(serialization, ",", true)
    val result = serializer.serialize(Seq(AtomicWrite(PersistentRepr("foo", 1L, "id", "event-manifest", writerUuid = writerUuid))))
    result should have size 1
    result.head should be a 'success

    val row = result.head.get.head
    row.message shouldBe defined
    row.event shouldBe defined
    row.sequenceNumber shouldBe 1L
    row.persistenceId shouldBe "id"
    row.eventManifest shouldBe Some("event-manifest")
    row.writerUuid shouldBe Some(writerUuid)
    // 1 is the Java serializer
    row.serId shouldBe Some(1)
  }

  it should "deserialize a row from the event column" in {
    val serializer = new ByteArrayJournalSerializer(serialization, ",", false)
    val result = serializer.serialize(Seq(AtomicWrite(PersistentRepr("foo", 1L, "id", "event-manifest", writerUuid = writerUuid))))
    result should have size 1
    result.head should be a 'success
    val row = result.head.get.head
    val r2 = serializer.deserialize(row)

    r2 should be a 'success
    val (rep, tags, ordering) = r2.get
    ordering shouldBe Long.MinValue

    rep.payload shouldBe "foo"
    rep.persistenceId shouldBe "id"
    rep.sequenceNr shouldBe 1L
    rep.manifest shouldBe "event-manifest"
  }

  it should "deserialize a row from the message column" in {
    val serializer = new ByteArrayJournalSerializer(serialization, ",", true)
    val result = serializer.serialize(Seq(AtomicWrite(PersistentRepr("foo", 1L, "id", "event-manifest", writerUuid = writerUuid))))
    result should have size 1
    result.head should be a 'success
    // Simulate an event that was written by pre 4.0.0
    val row = result.head.get.head
      .copy(event = None, eventManifest = None, serId = None, serManifest = None, writerUuid = None)
    val r2 = serializer.deserialize(row)

    r2 should be a 'success
    val (rep, tags, ordering) = r2.get
    ordering shouldBe Long.MinValue

    rep.payload shouldBe "foo"
    rep.persistenceId shouldBe "id"
    rep.sequenceNr shouldBe 1L
    rep.manifest shouldBe "event-manifest"
  }

  it should "not serialize a non-serializable message and indicate whether or not the serialization succeeded" in {
    class Test
    val serializer = new ByteArrayJournalSerializer(serialization, ",", false)
    val result = serializer.serialize(Seq(AtomicWrite(PersistentRepr(new Test))))
    result should have size 1
    result.head should be a 'failure
  }

  it should "not serialize a non-serializable message when writing the message column and indicate whether or not the serialization succeeded" in {
    class Test
    val serializer = new ByteArrayJournalSerializer(serialization, ",", true)
    val result = serializer.serialize(Seq(AtomicWrite(PersistentRepr(new Test))))
    result should have size 1
    result.head should be a 'failure
  }

  it should "serialize non-serializable and serializable messages and indicate whether or not the serialization succeeded" in {
    class Test
    val serializer = new ByteArrayJournalSerializer(serialization, ",", false)
    val result = serializer.serialize(List(AtomicWrite(PersistentRepr(new Test)), AtomicWrite(PersistentRepr("foo"))))
    result should have size 2
    result.head should be a 'failure
    result.last should be a 'success
  }
}