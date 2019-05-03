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

import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.serialization.SerializationExtension

import scala.collection.immutable._

class ByteArrayJournalSerializerTest extends SharedActorSystemTestSpec() {

  it should "serialize a serializable message and indicate whether or not the serialization succeeded" in {
    val serializer = new ByteArrayJournalSerializer(serialization, ",")
    val result = serializer.serialize(Seq(AtomicWrite(PersistentRepr("foo"))))
    result should have size 1
    result.head should be a Symbol("success")
  }

  it should "not serialize a non-serializable message and indicate whether or not the serialization succeeded" in {
    class Test
    val serializer = new ByteArrayJournalSerializer(serialization, ",")
    val result = serializer.serialize(Seq(AtomicWrite(PersistentRepr(new Test))))
    result should have size 1
    result.head should be a Symbol("failure")
  }

  it should "serialize non-serializable and serializable messages and indicate whether or not the serialization succeeded" in {
    class Test
    val serializer = new ByteArrayJournalSerializer(serialization, ",")
    val result = serializer.serialize(List(AtomicWrite(PersistentRepr(new Test)), AtomicWrite(PersistentRepr("foo"))))
    result should have size 2
    result.head should be a Symbol("failure")
    result.last should be a Symbol("success")
  }
}
