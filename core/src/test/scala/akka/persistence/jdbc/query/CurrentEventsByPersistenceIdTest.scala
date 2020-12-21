/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.query

import akka.Done
import akka.persistence.Persistence
import akka.persistence.jdbc.journal.JdbcAsyncWriteJournal
import akka.persistence.query.Offset
import akka.persistence.query.{ EventEnvelope, Sequence }
import akka.testkit.TestProbe

abstract class CurrentEventsByPersistenceIdTest(config: String) extends QueryTestSpec(config) {
  import QueryTestSpec.EventEnvelopeProbeOps

  it should "find events from sequenceNr" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      actor1 ! 1
      actor1 ! 2
      actor1 ! 3
      actor1 ! 4

      eventually {
        journalOps.countJournal.futureValue shouldBe 4
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 0, 1) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextEventEnvelope("my-1", 1, 1)
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 1, 1) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 1, 2) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 2, 2) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 2, 3) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 3, 3) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 0, 3) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 1, 3) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.expectComplete()
      }
    }
  }

  it should "not find any events for unknown pid" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    journalOps.withCurrentEventsByPersistenceId()("unkown-pid", 0L, Long.MaxValue) { tp =>
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }
  }

  it should "include ordering Offset in EventEnvelope" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      actor1 ! 1
      actor1 ! 2
      actor1 ! 3

      eventually {
        journalOps.countJournal.futureValue shouldBe 3
      }

      actor2 ! 4
      eventually {
        journalOps.countJournal.futureValue shouldBe 4
      }

      actor3 ! 5
      eventually {
        journalOps.countJournal.futureValue shouldBe 5
      }

      actor1 ! 6
      eventually {
        journalOps.countJournal.futureValue shouldBe 6
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 0, Long.MaxValue) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextEventEnvelope("my-1", 1, 1)
        tp.expectNextEventEnvelope("my-1", 2, 2)

        val env3 = tp.expectNext(ExpectNextTimeout)
        val ordering3 = env3.offset match {
          case Sequence(value) => value
        }

        val env6 = tp.expectNext(ExpectNextTimeout)
        env6.persistenceId shouldBe "my-1"
        env6.sequenceNr shouldBe 4
        env6.event shouldBe 6
        // event 4 and 5 persisted before 6 by different actors, increasing the ordering
        env6.offset shouldBe Offset.sequence(ordering3 + 3)

        tp.expectComplete()
      }
    }
  }

  it should "find events for actors" in withActorSystem { implicit system =>
    val journalOps = new JavaDslJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      actor1 ! 1
      actor1 ! 2
      actor1 ! 3

      eventually {
        journalOps.countJournal.futureValue shouldBe 3
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 1, 1) { tp =>
        tp.request(Int.MaxValue).expectNextEventEnvelope("my-1", 1, 1).expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 2, 2) { tp =>
        tp.request(Int.MaxValue).expectNextEventEnvelope("my-1", 2, 2).expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 3, 3) { tp =>
        tp.request(Int.MaxValue).expectNextEventEnvelope("my-1", 3, 3).expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 2, 3) { tp =>
        tp.request(Int.MaxValue)
          .expectNextEventEnvelope("my-1", 2, 2)
          .expectNextEventEnvelope("my-1", 3, 3)
          .expectComplete()
      }
    }
  }

  it should "allow updating events (for data migrations)" in withActorSystem { implicit system =>
    if (newDao)
      pending //https://github.com/akka/akka-persistence-jdbc/issues/469
    val journalOps = new JavaDslJdbcReadJournalOperations(system)
    val journal = Persistence(system).journalFor("")

    withTestActors() { (actor1, _, _) =>
      actor1 ! 1
      actor1 ! 2
      actor1 ! 3

      eventually {
        journalOps.countJournal.futureValue shouldBe 3
      }

      val pid = "my-1"
      journalOps.withCurrentEventsByPersistenceId()(pid, 1, 3) { tp =>
        tp.request(Int.MaxValue)
          .expectNextEventEnvelope(pid, 1, 1)
          .expectNextEventEnvelope(pid, 2, 2)
          .expectNextEventEnvelope(pid, 3, 3)
          .expectComplete()
      }

      // perform in-place update
      val journalP = TestProbe()
      journal.tell(JdbcAsyncWriteJournal.InPlaceUpdateEvent(pid, 1, Integer.valueOf(111)), journalP.ref)
      journalP.expectMsg(Done)

      journalOps.withCurrentEventsByPersistenceId()(pid, 1, 3) { tp =>
        tp.request(Int.MaxValue)
          .expectNextEventEnvelope(pid, 1, Integer.valueOf(111))
          .expectNextEventEnvelope(pid, 2, 2)
          .expectNextEventEnvelope(pid, 3, 3)
          .expectComplete()
      }
    }
  }
}

// Note: these tests use the shared-db configs, the test for all (so not only current) events use the regular db config

class H2ScalaCurrentEventsByPersistenceIdTest
    extends CurrentEventsByPersistenceIdTest("h2-shared-db-application.conf")
    with H2Cleaner
