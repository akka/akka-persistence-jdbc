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

package akka.persistence.jdbc.query

import akka.persistence.query.{EventEnvelope, NoOffset, Sequence}

import scala.concurrent.duration._
import akka.pattern.ask
import akka.persistence.journal.{EventSeq, ReadEventAdapter, Tagged, WriteEventAdapter}

object EventAdapterTest {

  case class Event(value: String) {
    def adapted = EventAdapted(value)
  }

  case class TaggedEvent(event: Event, tag: String)

  case class EventAdapted(value: String) {
    def restored = EventRestored(value)
  }

  case class EventRestored(value: String)

  class TestReadEventAdapter extends ReadEventAdapter {
    override def fromJournal(event: Any, manifest: String): EventSeq = event match {
      case e: EventAdapted => EventSeq.single(e.restored)
    }
  }

  class TestWriteEventAdapter extends WriteEventAdapter {
    override def manifest(event: Any): String = ""

    override def toJournal(event: Any): Any = event match {
      case e: Event                    => e.adapted
      case TaggedEvent(e: Event, tags) => Tagged(e.adapted, Set(tags))
      case _                           => event
    }
  }

}
/**
 * Tests that check persistence queries when event adapter is configured for persisted event.
 */
abstract class EventAdapterTest(config: String) extends QueryTestSpec(config) {
  import EventAdapterTest._

  final val NoMsgTime: FiniteDuration = 100.millis

  it should "apply event adapter when querying events for actor with pid 'my-1'" in {
    withTestActors() { (actor1, actor2, actor3) =>
      withEventsByPersistenceId()("my-1", 0) { tp =>
        tp.request(10)
        tp.expectNoMsg(100.millis)

        actor1 ! Event("1")
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-1", 1, EventRestored("1")))
        tp.expectNoMsg(100.millis)

        actor1 ! Event("2")
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-1", 2, EventRestored("2")))
        tp.expectNoMsg(100.millis)
        tp.cancel()
      }
    }

  }

  it should "apply event adapters when querying events by tag from an offset" in {
    withTestActors() { (actor1, actor2, actor3) =>

      (actor1 ? TaggedEvent(Event("1"), "event")).futureValue
      (actor2 ? TaggedEvent(Event("2"), "event")).futureValue
      (actor3 ? TaggedEvent(Event("3"), "event")).futureValue

      eventually {
        countJournal.futureValue shouldBe 3
      }

      withEventsByTag(10.seconds)("event", Sequence(1)) { tp =>

        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, EventRestored("2")))
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, EventRestored("3")))
        tp.expectNoMsg(NoMsgTime)

        actor1 ! TaggedEvent(Event("1"), "event")
        tp.expectNext(EventEnvelope(Sequence(4), "my-1", 2, EventRestored("1")))
        tp.cancel()
        tp.expectNoMsg(NoMsgTime)
      }
    }
  }

  it should "apply event adapters when querying current events for actors" in {
    withTestActors() { (actor1, actor2, actor3) =>
      actor1 ! Event("1")
      actor1 ! Event("2")
      actor1 ! Event("3")

      eventually {
        countJournal.futureValue shouldBe 3
      }

      withCurrentEventsByPersistenceId()("my-1", 1, 1) { tp =>
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(Sequence(1), "my-1", 1, EventRestored("1")))
          .expectComplete()
      }

      withCurrentEventsByPersistenceId()("my-1", 2, 2) { tp =>
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(Sequence(2), "my-1", 2, EventRestored("2")))
          .expectComplete()
      }

      withCurrentEventsByPersistenceId()("my-1", 3, 3) { tp =>
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(Sequence(3), "my-1", 3, EventRestored("3")))
          .expectComplete()
      }

      withCurrentEventsByPersistenceId()("my-1", 2, 3) { tp =>
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(Sequence(2), "my-1", 2, EventRestored("2")))
          .expectNext(EventEnvelope(Sequence(3), "my-1", 3, EventRestored("3")))
          .expectComplete()
      }
    }
  }

  it should "apply event adapters when querying all current events by tag" in {
    withTestActors() { (actor1, actor2, actor3) =>
      (actor1 ? TaggedEvent(Event("1"), "event")).futureValue
      (actor2 ? TaggedEvent(Event("2"), "event")).futureValue
      (actor3 ? TaggedEvent(Event("3"), "event")).futureValue

      eventually {
        countJournal.futureValue shouldBe 3
      }

      withCurrentEventsByTag()("event", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(1), _, _, EventRestored("1")) => }
        tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, EventRestored("2")) => }
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, EventRestored("3")) => }
        tp.expectComplete()
      }

      withCurrentEventsByTag()("event", Sequence(0)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(1), _, _, EventRestored("1")) => }
        tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, EventRestored("2")) => }
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, EventRestored("3")) => }
        tp.expectComplete()
      }

      withCurrentEventsByTag()("event", Sequence(1)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, EventRestored("2")) => }
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, EventRestored("3")) => }
        tp.expectComplete()
      }

      withCurrentEventsByTag()("event", Sequence(2)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, EventRestored("3")) => }
        tp.expectComplete()
      }

      withCurrentEventsByTag()("event", Sequence(3)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectComplete()
      }
    }
  }

}

class PostgresScalaEventAdapterTest extends EventAdapterTest("postgres-application.conf") with ScalaJdbcReadJournalOperations with PostgresCleaner

class MySQLScalaEventAdapterTest extends EventAdapterTest("mysql-application.conf") with ScalaJdbcReadJournalOperations with MysqlCleaner

class OracleScalaEventAdapterTest extends EventAdapterTest("oracle-application.conf") with ScalaJdbcReadJournalOperations with OracleCleaner

class H2ScalaEventAdapterTest extends EventAdapterTest("h2-application.conf") with ScalaJdbcReadJournalOperations with H2Cleaner
