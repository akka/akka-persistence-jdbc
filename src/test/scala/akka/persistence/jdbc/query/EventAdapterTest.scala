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

import akka.persistence.query.EventEnvelope

import scala.concurrent.duration._
import akka.pattern.ask
import akka.persistence.jdbc.query.adapter.DomainEvent

/**
  * Tests that check persistence queries when event adapter is configured for persisted event.
  * The test is configured to use [[akka.persistence.jdbc.query.adapter.DomainEventAdapter]] that creates
  * two event out of one persisted event.
  */
abstract class EventAdapterTest(config: String) extends QueryTestSpec(config) {

  final val NoMsgTime: FiniteDuration = 100.millis

  it should "find events for actor with pid 'my-1' using event adapters" in {
      withTestActors() { (actor1, actor2, actor3) =>
        withEventsByPersistenceId()("my-1", 0) { tp =>
          tp.request(10)
          tp.expectNoMsg(100.millis)

          actor1 ! DomainEvent("1")
          tp.expectNext(ExpectNextTimeout, EventEnvelope(1, "my-1", 1, DomainEvent("1")))
          tp.expectNext(ExpectNextTimeout, EventEnvelope(1, "my-1", 1, DomainEvent("11")))
          tp.expectNoMsg(100.millis)

          actor1 ! DomainEvent("2")
          tp.expectNext(ExpectNextTimeout, EventEnvelope(2, "my-1", 2, DomainEvent("2")))
          tp.expectNext(ExpectNextTimeout, EventEnvelope(2, "my-1", 2, DomainEvent("22")))
          tp.expectNoMsg(100.millis)
          tp.cancel()
        }
      }

  }

  it should "find events by tag from an offset when using event adapters" in {
    withTestActors() { (actor1, actor2, actor3) =>

      (actor1 ? withTags(DomainEvent("1"), "domainEvent")).futureValue
      (actor2 ? withTags(DomainEvent("2"), "domainEvent")).futureValue
      (actor3 ? withTags(DomainEvent("3"), "domainEvent")).futureValue

      eventually {
        countJournal.futureValue shouldBe 6 // each event is doubled
      }

      withEventsByTag(10.seconds)("domainEvent", 2) { tp =>

        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(2, "my-2", 1, DomainEvent("2")))
        tp.expectNext(EventEnvelope(2, "my-2", 1, DomainEvent("22")))
        tp.expectNext(EventEnvelope(3, "my-3", 1, DomainEvent("3")))
        tp.expectNext(EventEnvelope(3, "my-3", 1, DomainEvent("33")))
        tp.expectNoMsg(NoMsgTime)

        actor1 ! withTags(DomainEvent("1"), "domainEvent")
        tp.expectNext(EventEnvelope(4, "my-1", 2, DomainEvent("1")))
        tp.expectNext(EventEnvelope(4, "my-1", 2, DomainEvent("11")))
        tp.cancel()
        tp.expectNoMsg(NoMsgTime)
      }
    }
  }

  it should "find current events for actors when using event adapters" in {
    withTestActors() { (actor1, actor2, actor3) =>
      actor1 ! DomainEvent("1")
      actor1 ! DomainEvent("2")
      actor1 ! DomainEvent("3")

      eventually {
        countJournal.futureValue shouldBe 6 // events are doubled
      }

      withCurrentEventsByPersistenceId()("my-1", 1, 1) { tp =>
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(1, "my-1", 1, DomainEvent("1")))
          .expectNext(EventEnvelope(1, "my-1", 1, DomainEvent("11")))
          .expectComplete()
      }

      withCurrentEventsByPersistenceId()("my-1", 2, 2) { tp =>
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(2, "my-1", 2, DomainEvent("2")))
          .expectNext(EventEnvelope(2, "my-1", 2, DomainEvent("22")))
          .expectComplete()
      }

      withCurrentEventsByPersistenceId()("my-1", 3, 3) { tp =>
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(3, "my-1", 3, DomainEvent("3")))
          .expectNext(EventEnvelope(3, "my-1", 3, DomainEvent("33")))
          .expectComplete()
      }

      withCurrentEventsByPersistenceId()("my-1", 2, 3) { tp =>
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(2, "my-1", 2, DomainEvent("2")))
          .expectNext(EventEnvelope(2, "my-1", 2, DomainEvent("22")))
          .expectNext(EventEnvelope(3, "my-1", 3, DomainEvent("3")))
          .expectNext(EventEnvelope(3, "my-1", 3, DomainEvent("33")))
          .expectComplete()
      }
    }
  }

  it should "find all current events by tag when using event adapters" in {
    withTestActors() { (actor1, actor2, actor3) =>
      (actor1 ? withTags(DomainEvent("1"), "domainEvent")).futureValue
      (actor2 ? withTags(DomainEvent("2"), "domainEvent")).futureValue
      (actor3 ? withTags(DomainEvent("3"), "domainEvent")).futureValue

      eventually {
        countJournal.futureValue shouldBe 6 // events are doubled
      }

      withCurrentEventsByTag()("domainEvent", 0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(1, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(1, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(2, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(2, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(3, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(3, _, _, _) => }
        tp.expectComplete()
      }

      withCurrentEventsByTag()("domainEvent", 1) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(1, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(1, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(2, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(2, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(3, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(3, _, _, _) => }
        tp.expectComplete()
      }

      withCurrentEventsByTag()("domainEvent", 2) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(2, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(2, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(3, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(3, _, _, _) => }
        tp.expectComplete()
      }

      withCurrentEventsByTag()("domainEvent", 3) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(3, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(3, _, _, _) => }
        tp.expectComplete()
      }

      withCurrentEventsByTag()("domainEvent", 4) { tp =>
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