/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.query

import akka.Done
import akka.persistence.jdbc.query.EventAdapterTest.{ Event, TaggedAsyncEvent }
import akka.persistence.query.{ EventEnvelope, Sequence }
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.pattern.ask
import akka.persistence.query.Offset

abstract class EventsByPersistenceIdTest(config: String) extends QueryTestSpec(config) {
  import QueryTestSpec.EventEnvelopeProbeOps

  it should "not find any events for unknown pid" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    journalOps.withEventsByPersistenceId()("unkown-pid", 0L, Long.MaxValue) { tp =>
      tp.request(1)
      tp.expectNoMessage(100.millis)
      tp.cancel()
      tp.expectNoMessage(100.millis)
    }
  }

  it should "find events from sequenceNr" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      actor1 ! withTags(1, "number")
      actor1 ! withTags(2, "number")
      actor1 ! withTags(3, "number")
      actor1 ! withTags(4, "number")

      eventually {
        journalOps.countJournal.futureValue shouldBe 4
      }

      journalOps.withEventsByPersistenceId()("my-1", 0, 0) { tp =>
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-1", 0, 1) { tp =>
        tp.request(1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 1, 1)
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-1", 1, 1) { tp =>
        tp.request(1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 1, 1)
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-1", 1, 2) { tp =>
        tp.request(1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 1, 1)
        tp.request(1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 2, 2)
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-1", 2, 2) { tp =>
        tp.request(1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 2, 2)
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-1", 2, 3) { tp =>
        tp.request(1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 2, 2)
        tp.request(1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 3, 3)
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-1", 3, 3) { tp =>
        tp.request(1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 3, 3)
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-1", 0, 3) { tp =>
        tp.request(1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 1, 1)
        tp.request(1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 2, 2)
        tp.request(1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 3, 3)
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-1", 1, 3) { tp =>
        tp.request(1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 1, 1)
        tp.request(1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 2, 2)
        tp.request(1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 3, 3)
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }
    }
  }

  it should "include ordering Offset in EventEnvelope" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      actor1 ! withTags(1, "ordering")
      actor1 ! withTags(2, "ordering")
      actor1 ! withTags(3, "ordering")

      eventually {
        journalOps.countJournal.futureValue shouldBe 3
      }

      journalOps.withEventsByPersistenceId()("my-1", 0, Long.MaxValue) { tp =>
        tp.request(100)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 1, 1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 2, 2)

        val env3 = tp.expectNext(ExpectNextTimeout)
        val ordering3 = env3.offset match {
          case Sequence(value) => value
        }

        actor2 ! withTags(4, "ordering")
        eventually {
          journalOps.countJournal.futureValue shouldBe 4
        }
        actor3 ! withTags(5, "ordering")
        eventually {
          journalOps.countJournal.futureValue shouldBe 5
        }
        actor1 ! withTags(6, "ordering")
        eventually {
          journalOps.countJournal.futureValue shouldBe 6
        }

        val env6 = tp.expectNext(ExpectNextTimeout)
        env6.persistenceId shouldBe "my-1"
        env6.sequenceNr shouldBe 4
        env6.event shouldBe 6
        // event 4 and 5 persisted before 6 by different actors, increasing the ordering
        env6.offset shouldBe Offset.sequence(ordering3 + 3)

        tp.cancel()
      }
    }
  }

  it should "deliver EventEnvelopes non-zero timestamps" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
      val testStartTime = System.currentTimeMillis()

      (actor1 ? withTags(1, "number")).futureValue
      (actor2 ? withTags(2, "number")).futureValue
      (actor3 ? withTags(3, "number")).futureValue

      def assertTimestamp(timestamp: Long, clue: String) = {
        withClue(clue) {
          timestamp should !==(0L)
          // we want to prove that the event got a non-zero timestamp
          // but also a timestamp that between some boundaries around this test run
          (timestamp - testStartTime) should be < 120000L
          (timestamp - testStartTime) should be > 0L
        }
      }

      journalOps.withEventsByPersistenceId()("my-1", 0, 1) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF {
          case ev @ EventEnvelope(Sequence(1), "my-1", 1, 1) => assertTimestamp(ev.timestamp, "my-1")
        }
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-2", 0, 1) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF {
          case ev @ EventEnvelope(_, "my-2", 1, 2) => assertTimestamp(ev.timestamp, "my-2")
        }
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-3", 0, 1) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF {
          case ev @ EventEnvelope(_, "my-3", 1, 3) => assertTimestamp(ev.timestamp, "my-3")
        }
        tp.cancel()
      }
    }
  }

  it should "find events for actor with pid 'my-1'" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      journalOps.withEventsByPersistenceId()("my-1", 0) { tp =>
        tp.request(10)
        tp.expectNoMessage(100.millis)

        actor1 ! 1
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 1, 1)
        tp.expectNoMessage(100.millis)

        actor1 ! 2
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 2, 2)
        tp.expectNoMessage(100.millis)
        tp.cancel()
      }
    }
  }

  it should "find events for actor with pid 'my-1' and persisting messages to other actor" in withActorSystem {
    implicit system =>
      val journalOps = new JavaDslJdbcReadJournalOperations(system)
      withTestActors() { (actor1, actor2, actor3) =>
        journalOps.withEventsByPersistenceId()("my-1", 0, Long.MaxValue) { tp =>
          tp.request(10)
          tp.expectNoMessage(100.millis)

          actor1 ! 1
          tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 1, 1)
          tp.expectNoMessage(100.millis)

          actor1 ! 2
          tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 2, 2)
          tp.expectNoMessage(100.millis)

          actor2 ! 1
          actor2 ! 2
          actor2 ! 3
          tp.expectNoMessage(100.millis)

          actor1 ! 3
          tp.expectNextEventEnvelope(ExpectNextTimeout, "my-1", 3, 3)
          tp.expectNoMessage(100.millis)

          tp.cancel()
          tp.expectNoMessage(100.millis)
        }
      }
  }

  it should "find events for actor with pid 'my-2'" in withActorSystem { implicit system =>
    val journalOps = new JavaDslJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      actor2 ! 1
      actor2 ! 2
      actor2 ! 3

      eventually {
        journalOps.countJournal.futureValue shouldBe 3
      }

      journalOps.withEventsByPersistenceId()("my-2", 0, Long.MaxValue) { tp =>
        tp.request(1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-2", 1, 1)
        tp.request(1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-2", 2, 2)
        tp.request(1)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-2", 3, 3)
        tp.expectNoMessage(100.millis)

        actor2 ! 5
        actor2 ! 6
        actor2 ! 7

        eventually {
          journalOps.countJournal.futureValue shouldBe 6
        }

        tp.request(3)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-2", 4, 5)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-2", 5, 6)
        tp.expectNextEventEnvelope(ExpectNextTimeout, "my-2", 6, 7)
        tp.expectNoMessage(100.millis)

        tp.cancel()
        tp.expectNoMessage(100.millis)
      }
    }
  }

  it should "find a large number of events quickly" in withActorSystem { implicit system =>
    import akka.pattern.ask
    import system.dispatcher
    val journalOps = new JavaDslJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, _, _) =>
      def sendMessagesWithTag(tag: String, numberOfMessages: Int): Future[Done] = {
        val futures = for (i <- 1 to numberOfMessages) yield {
          actor1 ? TaggedAsyncEvent(Event(i.toString), tag)
        }
        Future.sequence(futures).map(_ => Done)
      }

      val tag = "someTag"
      val numberOfEvents = 10000
      // send a batch with a large number of events
      val batch = sendMessagesWithTag(tag, numberOfEvents)

      // wait for acknowledgement of the batch
      batch.futureValue

      journalOps.withEventsByPersistenceId()("my-1", 1, numberOfEvents) { tp =>
        val allEvents = tp.toStrict(atMost = 20.seconds)
        allEvents.size shouldBe numberOfEvents
      }
    }
  }
}

class PostgresScalaEventsByPersistenceIdTest
    extends EventsByPersistenceIdTest("postgres-application.conf")
    with PostgresCleaner

class MySQLScalaEventsByPersistenceIdTest extends EventsByPersistenceIdTest("mysql-application.conf") with MysqlCleaner

class OracleScalaEventsByPersistenceIdTest
    extends EventsByPersistenceIdTest("oracle-application.conf")
    with OracleCleaner

class SqlServerScalaEventsByPersistenceIdTest
    extends EventsByPersistenceIdTest("sqlserver-application.conf")
    with SqlServerCleaner

class H2ScalaEventsByPersistenceIdTest extends EventsByPersistenceIdTest("h2-application.conf") with H2Cleaner
