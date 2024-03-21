/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.query

import akka.persistence.query.{ EventEnvelope, NoOffset, Sequence }

import scala.concurrent.duration._
import akka.pattern.ask
import akka.persistence.journal.{ EventSeq, ReadEventAdapter, Tagged, WriteEventAdapter }
import org.scalatest.Assertions.fail

object EventAdapterTest {
  case class Event(value: String) {
    def adapted = EventAdapted(value)
  }

  case class TaggedEvent(event: Event, tag: String)

  case class TaggedAsyncEvent(event: Event, tag: String)

  case class EventAdapted(value: String) {
    def restored = EventRestored(value)
  }

  case class EventRestored(value: String)

  class TestReadEventAdapter extends ReadEventAdapter {
    override def fromJournal(event: Any, manifest: String): EventSeq =
      event match {
        case e: EventAdapted => EventSeq.single(e.restored)
        case _               => fail()
      }
  }

  class TestWriteEventAdapter extends WriteEventAdapter {
    override def manifest(event: Any): String = ""

    override def toJournal(event: Any): Any =
      event match {
        case e: Event                        => e.adapted
        case TaggedEvent(e: Event, tag)      => Tagged(e.adapted, Set(tag))
        case TaggedAsyncEvent(e: Event, tag) => Tagged(e.adapted, Set(tag))
        case _                               => event
      }
  }
}

/**
 * Tests that check persistence queries when event adapter is configured for persisted event.
 */
abstract class EventAdapterTest(config: String) extends QueryTestSpec(config) {
  import EventAdapterTest._

  final val NoMsgTime: FiniteDuration = 100.millis

  it should "apply event adapter when querying events for actor with pid 'my-1'" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors() { (actor1, _, _) =>
      journalOps.withEventsByPersistenceId()("my-1", 0) { tp =>
        tp.request(10)
        tp.expectNoMessage(100.millis)

        actor1 ! Event("1")
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-1", 1, EventRestored("1"), timestamp = 0L))
        tp.expectNoMessage(100.millis)

        actor1 ! Event("2")
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-1", 2, EventRestored("2"), timestamp = 0L))
        tp.expectNoMessage(100.millis)
        tp.cancel()
      }
    }
  }

  it should "apply event adapters when querying events by tag from an offset" in withActorSystem { implicit system =>

    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
      (actor1 ? TaggedEvent(Event("1"), "event")).futureValue
      (actor2 ? TaggedEvent(Event("2"), "event")).futureValue
      (actor3 ? TaggedEvent(Event("3"), "event")).futureValue

      eventually {
        journalOps.countJournal.futureValue shouldBe 3
      }

      journalOps.withEventsByTag(10.seconds)("event", Sequence(1)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, EventRestored("2"), timestamp = 0L))
        tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, EventRestored("3"), timestamp = 0L))
        tp.expectNoMessage(NoMsgTime)

        actor1 ? TaggedEvent(Event("1"), "event")
        tp.expectNext(EventEnvelope(Sequence(4), "my-1", 2, EventRestored("1"), timestamp = 0L))
        tp.cancel()
        tp.expectNoMessage(NoMsgTime)
      }
    }
  }

  it should "apply event adapters when querying current events for actors" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors() { (actor1, _, _) =>
      actor1 ! Event("1")
      actor1 ! Event("2")
      actor1 ! Event("3")

      eventually {
        journalOps.countJournal.futureValue shouldBe 3
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 1, 1) { tp =>
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(Sequence(1), "my-1", 1, EventRestored("1"), timestamp = 0L))
          .expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 2, 2) { tp =>
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(Sequence(2), "my-1", 2, EventRestored("2"), timestamp = 0L))
          .expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 3, 3) { tp =>
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(Sequence(3), "my-1", 3, EventRestored("3"), timestamp = 0L))
          .expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 2, 3) { tp =>
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(Sequence(2), "my-1", 2, EventRestored("2"), timestamp = 0L))
          .expectNext(EventEnvelope(Sequence(3), "my-1", 3, EventRestored("3"), timestamp = 0L))
          .expectComplete()
      }
    }
  }

  it should "apply event adapters when querying all current events by tag" in withActorSystem { implicit system =>

    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
      (actor1 ? TaggedEvent(Event("1"), "event")).futureValue
      (actor2 ? TaggedEvent(Event("2"), "event")).futureValue
      (actor3 ? TaggedEvent(Event("3"), "event")).futureValue

      eventually {
        journalOps.countJournal.futureValue shouldBe 3
      }

      journalOps.withCurrentEventsByTag()("event", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(1), _, _, EventRestored("1")) => }
        tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, EventRestored("2")) => }
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, EventRestored("3")) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("event", Sequence(0)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(1), _, _, EventRestored("1")) => }
        tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, EventRestored("2")) => }
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, EventRestored("3")) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("event", Sequence(1)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, EventRestored("2")) => }
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, EventRestored("3")) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("event", Sequence(2)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, EventRestored("3")) => }
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByTag()("event", Sequence(3)) { tp =>
        tp.request(Int.MaxValue)
        tp.expectComplete()
      }
    }
  }
}

class H2ScalaEventAdapterTest extends EventAdapterTest("h2-application.conf") with H2Cleaner
