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

import akka.Done
import akka.persistence.query.{ EventEnvelope, NoOffset, Sequence }
import akka.pattern.ask
import akka.persistence.jdbc.query.EventAdapterTest.{ Event, EventRestored, TaggedAsyncEvent, TaggedEvent }
import com.typesafe.config.{ ConfigValue, ConfigValueFactory }

import scala.concurrent.duration._
import scala.concurrent.Future

import EventsByTagTest._

object EventsByTagTest {
  val maxBufferSize = 20
  val refreshInterval = 500.milliseconds

  val configOverrides: Map[String, ConfigValue] = Map(
    "jdbc-read-journal.max-buffer-size" -> ConfigValueFactory.fromAnyRef(maxBufferSize.toString),
    "jdbc-read-journal.refresh-interval" -> ConfigValueFactory.fromAnyRef(refreshInterval.toString()))
}

abstract class EventsByTagTest(config: String) extends QueryTestSpec(config, configOverrides) {
  final val NoMsgTime: FiniteDuration = 100.millis

  it should "not find events for unknown tags" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      actor1 ! withTags(1, "one")
      actor2 ! withTags(2, "two")
      actor3 ! withTags(3, "three")

      eventually {
        journalOps.countJournal.futureValue shouldBe 3
      }

      journalOps.withEventsByTag()("unknown", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNoMessage(NoMsgTime)
        tp.cancel()
      }
    }
  }

  it should "find all events by tag" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
      (actor1 ? withTags(1, "number")).futureValue
      (actor2 ? withTags(2, "number")).futureValue
      (actor3 ? withTags(3, "number")).futureValue

      journalOps.withEventsByTag()("number", Sequence(Long.MinValue)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 3))
        tp.cancel()
      }

      journalOps.withEventsByTag()("number", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 3))
        tp.cancel()
      }

      journalOps.withEventsByTag()("number", Sequence(0)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 3))
        tp.cancel()
      }

      journalOps.withEventsByTag()("number", Sequence(1)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 3))
        tp.cancel()
      }

      journalOps.withEventsByTag()("number", Sequence(2)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 3))
        tp.cancel()
      }

      journalOps.withEventsByTag()("number", Sequence(3)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNoMessage(NoMsgTime)
        tp.cancel()
        tp.expectNoMessage(NoMsgTime)
      }

      journalOps.withEventsByTag()("number", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 3))
        tp.expectNoMessage(NoMsgTime)

        actor1 ? withTags(1, "number")
        tp.expectNext(EventEnvelope(Sequence(4), "my-1", 2, 1))

        actor1 ? withTags(1, "number")
        tp.expectNext(EventEnvelope(Sequence(5), "my-1", 3, 1))

        actor1 ? withTags(1, "number")
        tp.expectNext(EventEnvelope(Sequence(6), "my-1", 4, 1))
        tp.cancel()
        tp.expectNoMessage(NoMsgTime)
      }
    }
  }

  it should "find all events by tag even when lots of events are persisted concurrently" in withActorSystem {
    implicit system =>
      val journalOps = new ScalaJdbcReadJournalOperations(system)
      val msgCountPerActor = 20
      val numberOfActors = 100
      val totalNumberOfMessages = msgCountPerActor * numberOfActors
      withManyTestActors(numberOfActors) { (actors) =>
        val actorsWithIndexes = actors.zipWithIndex
        for {
          messageNumber <- 0 until msgCountPerActor
          (actor, actorIdx) <- actorsWithIndexes
        } actor ! TaggedEvent(Event(s"$actorIdx-$messageNumber"), "myEvent")

        journalOps.withEventsByTag()("myEvent", NoOffset) { tp =>
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
          tp.expectNoMessage(NoMsgTime)
        }
      }
  }

  it should "find events by tag from an offset" in withActorSystem { implicit system =>
    val journalOps = new JavaDslJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
      (actor1 ? withTags(1, "number")).futureValue
      (actor2 ? withTags(2, "number")).futureValue
      (actor3 ? withTags(3, "number")).futureValue

      eventually {
        journalOps.countJournal.futureValue shouldBe 3
      }

      journalOps.withEventsByTag()("number", Sequence(1)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 3))
        tp.expectNoMessage(NoMsgTime)

        actor1 ? withTags(1, "number")
        tp.expectNext(EventEnvelope(Sequence(4), "my-1", 2, 1))
        tp.cancel()
        tp.expectNoMessage(NoMsgTime)
      }
    }
  }

  it should "persist and find tagged event for one tag" in withActorSystem { implicit system =>
    val journalOps = new JavaDslJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      journalOps.withEventsByTag(10.seconds)("one", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNoMessage(NoMsgTime)

        actor1 ! withTags(1, "one") // 1
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNoMessage(NoMsgTime)

        actor2 ! withTags(1, "one") // 2
        tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, 1))
        tp.expectNoMessage(NoMsgTime)

        actor3 ! withTags(1, "one") // 3
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 1))
        tp.expectNoMessage(NoMsgTime)

        actor1 ! withTags(2, "two") // 4
        tp.expectNoMessage(NoMsgTime)

        actor2 ! withTags(2, "two") // 5
        tp.expectNoMessage(NoMsgTime)

        actor3 ! withTags(2, "two") // 6
        tp.expectNoMessage(NoMsgTime)

        actor1 ! withTags(1, "one") // 7
        tp.expectNext(EventEnvelope(Sequence(7), "my-1", 3, 1))
        tp.expectNoMessage(NoMsgTime)

        actor2 ! withTags(1, "one") // 8
        tp.expectNext(EventEnvelope(Sequence(8), "my-2", 3, 1))
        tp.expectNoMessage(NoMsgTime)

        actor3 ! withTags(1, "one") // 9
        tp.expectNext(EventEnvelope(Sequence(9), "my-3", 3, 1))
        tp.expectNoMessage(NoMsgTime)
        tp.cancel()
        tp.expectNoMessage(NoMsgTime)
      }
    }
  }

  it should "persist and find tagged events when stored with multiple tags" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
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
        journalOps.countJournal.futureValue shouldBe 12
      }

      journalOps.withEventsByTag(10.seconds)("prime", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.expectNext(EventEnvelope(Sequence(5), "my-1", 5, 5))
        tp.expectNext(EventEnvelope(Sequence(6), "my-2", 1, 3))
        tp.expectNext(EventEnvelope(Sequence(7), "my-3", 1, 3))
        tp.expectNoMessage(NoMsgTime)
        tp.cancel()
      }

      journalOps.withEventsByTag(10.seconds)("three", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.expectNext(EventEnvelope(Sequence(6), "my-2", 1, 3))
        tp.expectNext(EventEnvelope(Sequence(7), "my-3", 1, 3))
        tp.expectNoMessage(NoMsgTime)
        tp.cancel()
      }

      journalOps.withEventsByTag(10.seconds)("3", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.expectNext(EventEnvelope(Sequence(6), "my-2", 1, 3))
        tp.expectNext(EventEnvelope(Sequence(7), "my-3", 1, 3))
        tp.expectNoMessage(NoMsgTime)
        tp.cancel()
      }

      journalOps.withEventsByTag(10.seconds)("one", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNoMessage(NoMsgTime)
        tp.cancel()
      }

      journalOps.withEventsByTag(10.seconds)("four", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(4), "my-1", 4, 4) => }
        tp.expectNoMessage(NoMsgTime)
        tp.cancel()
      }

      journalOps.withEventsByTag(10.seconds)("five", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(5), "my-1", 5, 5))
        tp.expectNoMessage(NoMsgTime)
        tp.cancel()
        tp.expectNoMessage(NoMsgTime)
      }
    }
  }

  def timeoutMultiplier: Int = 1

  it should "show the configured performance characteristics" in withActorSystem { implicit system =>
    import system.dispatcher
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
      def sendMessagesWithTag(tag: String, numberOfMessagesPerActor: Int): Future[Done] = {
        val futures = for (actor <- Seq(actor1, actor2, actor3); i <- 1 to numberOfMessagesPerActor) yield {
          actor ? TaggedAsyncEvent(Event(i.toString), tag)
        }
        Future.sequence(futures).map(_ => Done)
      }

      val tag1 = "someTag"
      // send a batch of 3 * 50
      sendMessagesWithTag(tag1, 50)

      // start the query before the future completes
      journalOps.withEventsByTag()(tag1, NoOffset) { tp =>
        tp.within(5.seconds) {
          tp.request(Int.MaxValue)
          tp.expectNextN(150)
        }
        tp.expectNoMessage(NoMsgTime)

        // Send a small batch of 3 * 5 messages
        sendMessagesWithTag(tag1, 5)
        // Since queries are executed `refreshInterval`, there must be a small delay before this query gives a result
        tp.within(min = refreshInterval / 2, max = 2.seconds * timeoutMultiplier) {
          tp.expectNextN(15)
        }
        tp.expectNoMessage(NoMsgTime)

        // another large batch should be retrieved fast
        // send a second batch of 3 * 100
        sendMessagesWithTag(tag1, 100)
        tp.within(min = refreshInterval / 2, max = 10.seconds * timeoutMultiplier) {
          tp.request(Int.MaxValue)
          tp.expectNextN(300)
        }
        tp.expectNoMessage(NoMsgTime)
      }
    }
  }
}

class PostgresScalaEventsByTagTest extends EventsByTagTest("postgres-application.conf") with PostgresCleaner

class MySQLScalaEventByTagTest extends EventsByTagTest("mysql-application.conf") with MysqlCleaner

class OracleScalaEventByTagTest extends EventsByTagTest("oracle-application.conf") with OracleCleaner {
  override def timeoutMultiplier: Int = 4
}

class SqlServerScalaEventByTagTest extends EventsByTagTest("sqlserver-application.conf") with SqlServerCleaner

class H2ScalaEventsByTagTest extends EventsByTagTest("h2-application.conf") with H2Cleaner
