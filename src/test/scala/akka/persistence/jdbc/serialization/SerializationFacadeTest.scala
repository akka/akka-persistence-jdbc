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

package akka.persistence.jdbc.serialization

import akka.persistence.PersistentRepr
import akka.persistence.jdbc.TestSpec
import akka.persistence.jdbc.generator.AkkaPersistenceGen
import akka.persistence.jdbc.serialization.MockSerializationProxy
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink

import scala.concurrent.duration._
import scala.util.Try

class SerializationFacadeTest extends TestSpec("postgres-application.conf") {

  it should "serialize successfully" in {
    val facade = new SerializationFacade(MockSerializationProxy(PersistentRepr(""), fail = false), "$$$")
    forAll(AkkaPersistenceGen.genAtomicWrite) { aw ⇒
      val probe = Source.single(aw).via(facade.serialize)
        .runWith(TestSink.probe[Try[Iterable[Serialized]]])
        .request(Int.MaxValue)

      probe.within(10.seconds) {
        probe.expectNext() should be a 'success
      }
    }
  }

  it should "deserialize successfully" in {
    val facade = new SerializationFacade(MockSerializationProxy(PersistentRepr(""), fail = false), "$$$")
    forAll { (bytes: Array[Byte]) ⇒
      val probe = Source.single(bytes).via(facade.deserializeRepr)
        .runWith(TestSink.probe[Try[PersistentRepr]])
        .request(Int.MaxValue)

      probe.within(10.seconds) {
        probe.expectNext() should be a 'success
      }
    }
  }

  it should "fail to serialize" in {
    val facade = new SerializationFacade(MockSerializationProxy(PersistentRepr(""), fail = true), "$$$")
    forAll(AkkaPersistenceGen.genAtomicWrite) { aw ⇒
      val probe = Source.single(aw).via(facade.serialize)
        .runWith(TestSink.probe[Try[Iterable[Serialized]]])
        .request(Int.MaxValue)

      probe.within(10.seconds) {
        probe.expectNext() should be a 'failure
      }
    }
  }

  it should "fail to deserialize" in {
    val facade = new SerializationFacade(MockSerializationProxy(PersistentRepr(""), fail = true), "$$$")
    forAll { (bytes: Array[Byte]) ⇒
      val probe = Source.single(bytes).via(facade.deserializeRepr)
        .runWith(TestSink.probe[Try[PersistentRepr]])
        .request(Int.MaxValue)

      probe.within(10.seconds) {
        probe.expectNext should be a 'failure
      }
    }
  }
}
