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

package akka.persistence.jdbc.serialization

import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.persistence.jdbc.TestSpec
import akka.persistence.jdbc.generator.AkkaPersistenceGen
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink

import scala.concurrent.duration._
import scala.util.Try

class SerializationFacadeTest extends TestSpec("postgres-application.conf") {

  it should "serialize a serializable message and indicate whether or not the serialization succeeded" in {
    val facade = new SerializationFacade(AkkaSerializationProxy(serialization), ",")
    val probe = Source.single(AtomicWrite(PersistentRepr("foo"))).via(facade.serialize(true))
      .runWith(TestSink.probe[Try[Iterable[SerializationResult]]])
      .request(Int.MaxValue)

    probe.within(10.seconds) {
      probe.expectNext() should be a 'success
    }
  }

  it should "not serialize a non-serializable message and indicate whether or not the serialization succeeded" in {
    class Test
    val facade = new SerializationFacade(AkkaSerializationProxy(serialization), ",")
    val probe = Source.single(AtomicWrite(PersistentRepr(new Test))).via(facade.serialize(true))
      .runWith(TestSink.probe[Try[Iterable[SerializationResult]]])
      .request(Int.MaxValue)

    probe.within(10.seconds) {
      probe.expectNext() should be a 'failure
    }
  }

  it should "serialize non-serializable and serializable messages and indicate whether or not the serialization succeeded" in {
    class Test
    val facade = new SerializationFacade(AkkaSerializationProxy(serialization), ",")
    val probe = Source(List(AtomicWrite(PersistentRepr(new Test)), AtomicWrite(PersistentRepr("foo")))).via(facade.serialize(true))
      .runWith(TestSink.probe[Try[Iterable[SerializationResult]]])
      .request(Int.MaxValue)

    probe.within(10.seconds) {
      probe.expectNext() should be a 'failure
      probe.expectNext() should be a 'success
    }
  }
}
