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

package akka.persistence.jdbc.dao

import akka.persistence.jdbc.TestSpec
import akka.persistence.jdbc.generator.AkkaPersistenceGen
import akka.persistence.jdbc.serialization.Serialized
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink

import scala.util.{ Try, Success }
import scala.concurrent.duration._

class FlowGraphWriteMessageFacadeTest extends TestSpec {
  it should "successfully write messages when the DAO returns success for each write operation" in {
    val facade = new FlowGraphWriteMessagesFacade(new MockJournalDao(fail = false))
    forAll(AkkaPersistenceGen.genListOfSerialized) { xs ⇒
      val probe = Source.single(Success(xs)).via(facade.writeMessages)
        .runWith(TestSink.probe[Try[Iterable[Serialized]]])
        .request(Int.MaxValue)

      probe.within(10.seconds) {
        probe.expectNext() should be a 'success
        probe.expectComplete()
      }
    }
  }

  it should "fail when the DAO returns a failure for each write operation" in {
    val facade = new FlowGraphWriteMessagesFacade(new MockJournalDao(fail = true))
    forAll(AkkaPersistenceGen.genListOfSerialized) { xs ⇒
      val probe = Source.single(Success(xs)).via(facade.writeMessages)
        .runWith(TestSink.probe[Try[Iterable[Serialized]]])
        .request(Int.MaxValue)

      probe.within(10.seconds) {
        probe.expectError()
      }
    }
  }
}
