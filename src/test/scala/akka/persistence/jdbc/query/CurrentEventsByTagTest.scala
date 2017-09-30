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
import akka.pattern.ask
import com.typesafe.config.{ConfigValue, ConfigValueFactory}
import scala.concurrent.duration._
import akka.Done
import akka.persistence.jdbc.query.EventAdapterTest.{Event, TaggedAsyncEvent}

import scala.concurrent.Future

import CurrentEventsByTagTest._

object CurrentEventsByTagTest {
  val maxBufferSize = 20
  val refreshInterval = 500.milliseconds

  val configOverrides: Map[String, ConfigValue] = Map(
    "jdbc-read-journal.max-buffer-size" -> ConfigValueFactory.fromAnyRef(maxBufferSize.toString),
    "jdbc-read-journal.refresh-interval" -> ConfigValueFactory.fromAnyRef(refreshInterval.toString())
  )
}

abstract class CurrentEventsByTagTest(config: String) extends QueryTestSpec(config, configOverrides) with ScalaJdbcReadJournalOperations {

  it should "not find an event by tag for unknown tag" in {
    withTestActors() { (actor1, actor2, actor3) =>
      (actor1 ? withTags(1, "one")).futureValue
      (actor2 ? withTags(2, "two")).futureValue
      (actor3 ? withTags(3, "three")).futureValue

      eventually {
        countJournal.futureValue shouldBe 3
      }
      eventually {
        latestOrdering.futureValue shouldBe 3
      }

      withCurrentEventsByTag()("unknown", 0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectComplete()
      }
    }
  }

  it should "find all events by tag" in {
    withTestActors() { (actor1, actor2, actor3) =>
      (actor1 ? withTags(1, "number")).futureValue
      (actor2 ? withTags(2, "number")).futureValue
      (actor3 ? withTags(3, "number")).futureValue

      eventually {
        countJournal.futureValue shouldBe 3
      }
      eventually {
        latestOrdering.futureValue shouldBe 3
      }

      withCurrentEventsByTag()("number", 0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(1, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(2, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(3, _, _, _) => }
        tp.expectComplete()
      }

      withCurrentEventsByTag()("number", 1) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(2, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(3, _, _, _) => }
        tp.expectComplete()
      }

      withCurrentEventsByTag()("number", 2) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(3, _, _, _) => }
        tp.expectComplete()
      }

      withCurrentEventsByTag()("number", 3) { tp =>
        tp.request(Int.MaxValue)
        tp.expectComplete()
      }
    }
  }

  it should "persist and find a tagged event with multiple tags" in
    withTestActors() { (actor1, actor2, actor3) =>
      withClue("Persisting multiple tagged events") {
        (actor1 ? withTags(1, "one", "1", "prime")).futureValue
        (actor1 ? withTags(2, "two", "2", "prime")).futureValue
        (actor1 ? withTags(3, "three", "3", "prime")).futureValue
        (actor1 ? withTags(4, "four", "4")).futureValue
        (actor1 ? withTags(5, "five", "5", "prime")).futureValue

        (actor2 ? withTags(3, "three", "3", "prime")).futureValue
        (actor3 ? withTags(3, "three", "3", "prime")).futureValue

        (actor1 ? 1).futureValue
        (actor1 ? 1).futureValue

        eventually {
          countJournal.futureValue shouldBe 9
        }
        eventually {
          latestOrdering.futureValue shouldBe 9
        }
      }

      withCurrentEventsByTag()("one", 0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(1, _, _, _) => }
        tp.expectComplete()
      }

      withCurrentEventsByTag()("prime", 0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(1, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(2, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(3, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(5, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(6, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(7, _, _, _) => }
        tp.expectComplete()
      }

      withCurrentEventsByTag()("3", 0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(3, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(6, _, _, _) => }
        tp.expectNextPF { case EventEnvelope(7, _, _, _) => }
        tp.expectComplete()
      }

      withCurrentEventsByTag()("4", 0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(4, _, _, _) => }
        tp.expectComplete()
      }

      withCurrentEventsByTag()("four", 0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(4, _, _, _) => }
        tp.expectComplete()
      }

      withCurrentEventsByTag()("5", 0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(5, _, _, _) => }
        tp.expectComplete()
      }

      withCurrentEventsByTag()("five", 0) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(5, _, _, _) => }
        tp.expectComplete()
      }
    }

  it should "complete without any gaps in case events are being persisted when the query is executed" in {
    withTestActors() { (actor1, actor2, actor3) =>
      def sendMessagesWithTag(tag: String, numberOfMessagesPerActor: Int): Future[Done] = {
        val futures = for (actor <- Seq(actor1, actor2, actor3); i <- 1 to numberOfMessagesPerActor) yield {
          actor ? TaggedAsyncEvent(Event(i.toString), tag)
        }
        Future.sequence(futures).map(_ => Done)
      }

      val tag = "someTag"
      // send a batch of 3 * 200
      val batch1 = sendMessagesWithTag(tag, 200)
      // Try to persist a large batch of events per actor. Some of these may be returned, but not all!
      sendMessagesWithTag(tag, 10000)

      // wait for acknowledgement of the first batch only
      batch1.futureValue
      // Sanity check, all events in the first batch must be in the journal
      countJournal.futureValue should be >= 600L

      // start the query before the last batch completes
      withCurrentEventsByTag()(tag, 0) { tp =>
        // The stream must complete within the given amount of time
        // This make take a while in case the journal sequence actor detects gaps
        val allEvents = tp.toStrict(atMost = 20.seconds)
        allEvents.size should be >= 600
        val expectedOffsets = 1L.to(allEvents.size)
        allEvents.map(_.offset) shouldBe expectedOffsets
      }
    }
  }
}

class PostgresScalaCurrentEventsByTagTest extends CurrentEventsByTagTest("postgres-application.conf") with PostgresCleaner

class MySQLScalaCurrentEventsByTagTest extends CurrentEventsByTagTest("mysql-application.conf") with MysqlCleaner

class OracleScalaCurrentEventsByTagTest extends CurrentEventsByTagTest("oracle-application.conf") with OracleCleaner

class H2ScalaCurrentEventsByTagTest extends CurrentEventsByTagTest("h2-application.conf") with H2Cleaner
