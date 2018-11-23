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

import akka.persistence.query.{ EventEnvelope, NoOffset, Sequence }
import akka.pattern.ask
import com.typesafe.config.{ ConfigValue, ConfigValueFactory }

import scala.concurrent.duration._
import akka.Done
import akka.persistence.jdbc.query.EventAdapterTest.{ Event, TaggedAsyncEvent }

import scala.concurrent.Future
import CurrentEventsByTagTest._

object CurrentEventsByTagTest {
  val maxBufferSize = 20
  val refreshInterval = 500.milliseconds

  val configOverrides: Map[String, ConfigValue] = Map(
    "jdbc-read-journal.max-buffer-size" -> ConfigValueFactory.fromAnyRef(maxBufferSize.toString),
    "jdbc-read-journal.refresh-interval" -> ConfigValueFactory.fromAnyRef(refreshInterval.toString()))
}

abstract class CurrentEventsByTagTest(config: String) extends QueryTestSpec(config, configOverrides) {

  it should "not find an event by tag for unknown tag" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
      (actor1 ? withTags(1, "one")).futureValue
      (actor2 ? withTags(2, "two")).futureValue
      (actor3 ? withTags(3, "three")).futureValue

      eventually {
        journalOps.countJournal.futureValue shouldBe 3
      }

      journalOps.withCurrentEventsByTag()("unknown", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectComplete()
      }
    }
  }

  it should "find all events by tag" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
      (actor1 ? withTags(1, "number")).futureValue
      (actor2 ? withTags(2, "number")).futureValue
      (actor3 ? withTags(3, "number")).futureValue

      eventually {
        journalOps.countJournal.futureValue shouldBe 3
      }

      journalOps.withCurrentEventsByTag()("number", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(1), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("number", Sequence(0)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(1), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("number", Sequence(1)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("number", Sequence(2)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("number", Sequence(3)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectComplete()
      }
    }
  }

  it should "persist and find a tagged event with multiple tags" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
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
          journalOps.countJournal.futureValue shouldBe 9
        }
      }

      journalOps.withCurrentEventsByTag()("one", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(1), _, _, _) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("prime", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(1), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(5), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(6), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(7), _, _, _) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("3", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(6), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(7), _, _, _) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("4", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(4), _, _, _) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("four", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(4), _, _, _) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("5", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(5), _, _, _) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("five", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(5), _, _, _) => }
        tp.expectComplete()
      }
    }
  }

  it should "complete without any gaps in case events are being persisted when the query is executed" in withActorSystem { implicit system =>
    val journalOps = new JavaDslJdbcReadJournalOperations(system)
    import system.dispatcher
    withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
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
      val batch2 = sendMessagesWithTag(tag, 10000)

      // wait for acknowledgement of the first batch only
      batch1.futureValue
      // Sanity check, all events in the first batch must be in the journal
      journalOps.countJournal.futureValue should be >= 600L

      // start the query before the last batch completes
      journalOps.withCurrentEventsByTag()(tag, NoOffset) { tp =>
        // The stream must complete within the given amount of time
        // This make take a while in case the journal sequence actor detects gaps
        val allEvents = tp.toStrict(atMost = 20.seconds)
        allEvents.size should be >= 600
        val expectedOffsets = 1L.to(allEvents.size).map(Sequence.apply)
        allEvents.map(_.offset) shouldBe expectedOffsets
      }
      batch2.futureValue
    }
  }
}

// Note: these tests use the shared-db configs, the test for all (so not only current) events use the regular db config

class PostgresScalaCurrentEventsByTagTest extends CurrentEventsByTagTest("postgres-shared-db-application.conf") with PostgresCleaner

class MySQLScalaCurrentEventsByTagTest extends CurrentEventsByTagTest("mysql-shared-db-application.conf") with MysqlCleaner

class OracleScalaCurrentEventsByTagTest extends CurrentEventsByTagTest("oracle-shared-db-application.conf") with OracleCleaner

class SqlServerScalaCurrentEventsByTagTest extends CurrentEventsByTagTest("sqlserver-shared-db-application.conf") with SqlServerCleaner

class H2ScalaCurrentEventsByTagTest extends CurrentEventsByTagTest("h2-shared-db-application.conf") with H2Cleaner
