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
import akka.persistence.jdbc.query.EventAdapterTest.{Event, EventRestored, TaggedEvent}

abstract class EventsByTagTest(config: String) extends QueryTestSpec(config) {

  final val NoMsgTime: FiniteDuration = 100.millis

  it should "not find events for unknown tags" in {
    withTestActors() { (actor1, actor2, actor3) =>
      actor1 ! withTags(1, "one")
      actor2 ! withTags(2, "two")
      actor3 ! withTags(3, "three")

      eventually {
        countJournal.futureValue shouldBe 3
      }

      withEventsByTag()("unknown", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNoMsg(NoMsgTime)
        tp.cancel()
      }
    }
  }

  it should "find all events by tag" in {
    withTestActors() { (actor1, actor2, actor3) =>
      (actor1 ? withTags(1, "number")).futureValue
      (actor2 ? withTags(2, "number")).futureValue
      (actor3 ? withTags(3, "number")).futureValue

      withEventsByTag()("number", Sequence(Long.MinValue)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 3))
        tp.cancel()
      }

      withEventsByTag()("number", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 3))
        tp.cancel()
      }

      withEventsByTag()("number", Sequence(0)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 3))
        tp.cancel()
      }

      withEventsByTag()("number", Sequence(1)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 3))
        tp.cancel()
      }

      withEventsByTag()("number", Sequence(2)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 3))
        tp.cancel()
      }

      withEventsByTag()("number", Sequence(3)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNoMsg(NoMsgTime)
        tp.cancel()
        tp.expectNoMsg(NoMsgTime)
      }

      withEventsByTag()("number", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 3))
        tp.expectNoMsg(NoMsgTime)

        actor1 ! withTags(1, "number")
        tp.expectNext(EventEnvelope(Sequence(4), "my-1", 2, 1))

        actor1 ! withTags(1, "number")
        tp.expectNext(EventEnvelope(Sequence(5), "my-1", 3, 1))

        actor1 ! withTags(1, "number")
        tp.expectNext(EventEnvelope(Sequence(6), "my-1", 4, 1))
        tp.cancel()
        tp.expectNoMsg(NoMsgTime)
      }
    }
  }

  it should "find all events by tag even when lots of events are persisted concurrently" in {
    val msgCountPerActor = 20
    val numberOfActors = 100
    val totalNumberOfMessages = msgCountPerActor * numberOfActors
    withManyTestActors(numberOfActors) { (actors) =>
      val actorsWithIndexes = actors.zipWithIndex
      for {
        messageNumber <- 0 until msgCountPerActor
        (actor, actorIdx) <- actorsWithIndexes
      } actor ! TaggedEvent(Event(s"$actorIdx-$messageNumber"), "myEvent")

      withEventsByTag()("myEvent", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        (1 to totalNumberOfMessages).foldLeft(Map.empty[Int, Int]) { (map, _) =>
          val mgsParts = tp.expectNext().event.asInstanceOf[EventRestored].value.split("-")
          val actorIdx = mgsParts(0).toInt
          val msgNumber = mgsParts(1).toInt
          val expectedCount = map.getOrElse(actorIdx, 0)
          assertResult(expected = expectedCount)(msgNumber)
          // keep track of the next message number we expect for this actor idx
          map.updated(actorIdx, msgNumber + 1)
        }
        tp.cancel()
        tp.expectNoMsg(NoMsgTime)
      }
    }
  }

  it should "find events by tag from an offset" in {
    withTestActors() { (actor1, actor2, actor3) =>
      (actor1 ? withTags(1, "number")).futureValue
      (actor2 ? withTags(2, "number")).futureValue
      (actor3 ? withTags(3, "number")).futureValue

      eventually {
        countJournal.futureValue shouldBe 3
      }

      withEventsByTag()("number", Sequence(1)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 3))
        tp.expectNoMsg(NoMsgTime)

        actor1 ! withTags(1, "number")
        tp.expectNext(EventEnvelope(Sequence(4), "my-1", 2, 1))
        tp.cancel()
        tp.expectNoMsg(NoMsgTime)
      }
    }
  }

  it should "persist and find tagged event for one tag" in {
    withTestActors() { (actor1, actor2, actor3) =>
      withEventsByTag(10.seconds)("one", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNoMsg(NoMsgTime)

        actor1 ! withTags(1, "one") // 1
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNoMsg(NoMsgTime)

        actor2 ! withTags(1, "one") // 2
        tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, 1))
        tp.expectNoMsg(NoMsgTime)

        actor3 ! withTags(1, "one") // 3
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 1))
        tp.expectNoMsg(NoMsgTime)

        actor1 ! withTags(2, "two") // 4
        tp.expectNoMsg(NoMsgTime)

        actor2 ! withTags(2, "two") // 5
        tp.expectNoMsg(NoMsgTime)

        actor3 ! withTags(2, "two") // 6
        tp.expectNoMsg(NoMsgTime)

        actor1 ! withTags(1, "one") // 7
        tp.expectNext(EventEnvelope(Sequence(7), "my-1", 3, 1))
        tp.expectNoMsg(NoMsgTime)

        actor2 ! withTags(1, "one") // 8
        tp.expectNext(EventEnvelope(Sequence(8), "my-2", 3, 1))
        tp.expectNoMsg(NoMsgTime)

        actor3 ! withTags(1, "one") // 9
        tp.expectNext(EventEnvelope(Sequence(9), "my-3", 3, 1))
        tp.expectNoMsg(NoMsgTime)
        tp.cancel()
        tp.expectNoMsg(NoMsgTime)
      }
    }
  }

  it should "persist and find tagged events when stored with multiple tags" in {
    withTestActors() { (actor1, actor2, actor3) =>
      (actor1 ? withTags(1, "one", "1", "prime")).futureValue
      (actor1 ? withTags(2, "two", "2", "prime")).futureValue
      (actor1 ? withTags(3, "three", "3", "prime")).futureValue
      (actor1 ? withTags(4, "four", "4")).futureValue
      (actor1 ? withTags(5, "five", "5", "prime")).futureValue
      (actor2 ? withTags(3, "three", "3", "prime")).futureValue
      (actor3 ? withTags(3, "three", "3", "prime")).futureValue

      (actor1 ? 6).futureValue
      (actor1 ? 7).futureValue
      (actor1 ? 8).futureValue
      (actor1 ? 9).futureValue
      (actor1 ? 10).futureValue

      eventually {
        countJournal.futureValue shouldBe 12
      }

      withEventsByTag(10.seconds)("prime", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.expectNext(EventEnvelope(Sequence(5), "my-1", 5, 5))
        tp.expectNext(EventEnvelope(Sequence(6), "my-2", 1, 3))
        tp.expectNext(EventEnvelope(Sequence(7), "my-3", 1, 3))
        tp.expectNoMsg(NoMsgTime)
        tp.cancel()
      }

      withEventsByTag(10.seconds)("three", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.expectNext(EventEnvelope(Sequence(6), "my-2", 1, 3))
        tp.expectNext(EventEnvelope(Sequence(7), "my-3", 1, 3))
        tp.expectNoMsg(NoMsgTime)
        tp.cancel()
      }

      withEventsByTag(10.seconds)("3", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.expectNext(EventEnvelope(Sequence(6), "my-2", 1, 3))
        tp.expectNext(EventEnvelope(Sequence(7), "my-3", 1, 3))
        tp.expectNoMsg(NoMsgTime)
        tp.cancel()
      }

      withEventsByTag(10.seconds)("one", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNoMsg(NoMsgTime)
        tp.cancel()
      }

      withEventsByTag(10.seconds)("four", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(4), "my-1", 4, 4) => }
        tp.expectNoMsg(NoMsgTime)
        tp.cancel()
      }

      withEventsByTag(10.seconds)("five", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(5), "my-1", 5, 5))
        tp.expectNoMsg(NoMsgTime)
        tp.cancel()
        tp.expectNoMsg(NoMsgTime)
      }
    }
  }
}

class PostgresScalaEventsByTagTest extends EventsByTagTest("postgres-application.conf") with ScalaJdbcReadJournalOperations with PostgresCleaner

class MySQLScalaEventByTagTest extends EventsByTagTest("mysql-application.conf") with ScalaJdbcReadJournalOperations with MysqlCleaner

class OracleScalaEventByTagTest extends EventsByTagTest("oracle-application.conf") with ScalaJdbcReadJournalOperations with OracleCleaner

class H2ScalaEventsByTagTest extends EventsByTagTest("h2-application.conf") with ScalaJdbcReadJournalOperations with H2Cleaner
