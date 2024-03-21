/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.query

import akka.actor.{ ActorRef, ActorSystem, Props, Stash, Status }
import akka.pattern.ask
import akka.event.LoggingReceive
import akka.persistence.{ DeleteMessagesFailure, DeleteMessagesSuccess, PersistentActor }
import akka.persistence.jdbc.SingleActorSystemPerTestSpec
import akka.persistence.jdbc.query.EventAdapterTest.{ Event, TaggedAsyncEvent, TaggedEvent }
import akka.persistence.jdbc.query.javadsl.{ JdbcReadJournal => JavaJdbcReadJournal }
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.journal.Tagged
import akka.persistence.query.{ EventEnvelope, Offset, PersistenceQuery }
import akka.stream.scaladsl.Sink
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.javadsl.{ TestSink => JavaSink }
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.{ Materializer, SystemMaterializer }
import com.typesafe.config.ConfigValue

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.persistence.jdbc.testkit.internal.H2
import akka.persistence.jdbc.testkit.internal.MySQL
import akka.persistence.jdbc.testkit.internal.Oracle
import akka.persistence.jdbc.testkit.internal.Postgres
import akka.persistence.jdbc.testkit.internal.SqlServer

import scala.concurrent.ExecutionContext

trait ReadJournalOperations {
  def withCurrentPersistenceIds(within: FiniteDuration = 60.second)(f: TestSubscriber.Probe[String] => Unit): Unit
  def withPersistenceIds(within: FiniteDuration = 60.second)(f: TestSubscriber.Probe[String] => Unit): Unit
  def withCurrentEventsByPersistenceId(within: FiniteDuration = 60.second)(
      persistenceId: String,
      fromSequenceNr: Long = 0,
      toSequenceNr: Long = Long.MaxValue)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit
  def withEventsByPersistenceId(within: FiniteDuration = 60.second)(
      persistenceId: String,
      fromSequenceNr: Long = 0,
      toSequenceNr: Long = Long.MaxValue)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit
  def withCurrentEventsByTag(within: FiniteDuration = 60.second)(tag: String, offset: Offset)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit
  def withEventsByTag(within: FiniteDuration = 60.second)(tag: String, offset: Offset)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit
  def countJournal: Future[Long]
}

class ScalaJdbcReadJournalOperations(readJournal: JdbcReadJournal)(implicit system: ActorSystem, mat: Materializer)
    extends ReadJournalOperations {
  def this(system: ActorSystem) =
    this(PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier))(
      system,
      SystemMaterializer(system).materializer)

  import system.dispatcher

  def withCurrentPersistenceIds(within: FiniteDuration)(f: TestSubscriber.Probe[String] => Unit): Unit = {
    val tp = readJournal.currentPersistenceIds().runWith(TestSink[String]())
    tp.within(within)(f(tp))
  }

  def withPersistenceIds(within: FiniteDuration)(f: TestSubscriber.Probe[String] => Unit): Unit = {
    val tp = readJournal.persistenceIds().runWith(TestSink[String]())
    tp.within(within)(f(tp))
  }

  def withCurrentEventsByPersistenceId(
      within: FiniteDuration)(persistenceId: String, fromSequenceNr: Long = 0, toSequenceNr: Long = Long.MaxValue)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal
      .currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr)
      .runWith(TestSink[EventEnvelope]())
    tp.within(within)(f(tp))
  }

  def withEventsByPersistenceId(
      within: FiniteDuration)(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp =
      readJournal.eventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).runWith(TestSink[EventEnvelope]())
    tp.within(within)(f(tp))
  }

  def withCurrentEventsByTag(within: FiniteDuration)(tag: String, offset: Offset)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.currentEventsByTag(tag, offset).runWith(TestSink[EventEnvelope]())
    tp.within(within)(f(tp))
  }

  def withEventsByTag(within: FiniteDuration)(tag: String, offset: Offset)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.eventsByTag(tag, offset).runWith(TestSink[EventEnvelope]())
    tp.within(within)(f(tp))
  }

  override def countJournal: Future[Long] =
    readJournal
      .currentPersistenceIds()
      .filter(pid => (1 to 3).map(id => s"my-$id").contains(pid))
      .mapAsync(1) { pid =>
        readJournal.currentEventsByPersistenceId(pid, 0, Long.MaxValue).map(_ => 1L).runWith(Sink.seq).map(_.sum)
      }
      .runWith(Sink.seq)
      .map(_.sum)
}

class JavaDslJdbcReadJournalOperations(readJournal: javadsl.JdbcReadJournal)(
    implicit system: ActorSystem,
    mat: Materializer)
    extends ReadJournalOperations {
  def this(system: ActorSystem) =
    this(
      PersistenceQuery.get(system).getReadJournalFor(classOf[javadsl.JdbcReadJournal], JavaJdbcReadJournal.Identifier))(
      system,
      SystemMaterializer(system).materializer)

  import system.dispatcher

  def withCurrentPersistenceIds(within: FiniteDuration)(f: TestSubscriber.Probe[String] => Unit): Unit = {
    val sink: akka.stream.javadsl.Sink[String, TestSubscriber.Probe[String]] = JavaSink.create[String](system)
    val tp = readJournal.currentPersistenceIds().runWith(sink, mat)
    tp.within(within)(f(tp))
  }

  def withPersistenceIds(within: FiniteDuration)(f: TestSubscriber.Probe[String] => Unit): Unit = {
    val sink: akka.stream.javadsl.Sink[String, TestSubscriber.Probe[String]] = JavaSink.create[String](system)
    val tp = readJournal.persistenceIds().runWith(sink, mat)
    tp.within(within)(f(tp))
  }

  def withCurrentEventsByPersistenceId(
      within: FiniteDuration)(persistenceId: String, fromSequenceNr: Long = 0, toSequenceNr: Long = Long.MaxValue)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val sink: akka.stream.javadsl.Sink[EventEnvelope, TestSubscriber.Probe[EventEnvelope]] =
      JavaSink.create[EventEnvelope](system)
    val tp = readJournal.currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).runWith(sink, mat)
    tp.within(within)(f(tp))
  }

  def withEventsByPersistenceId(
      within: FiniteDuration)(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val sink: akka.stream.javadsl.Sink[EventEnvelope, TestSubscriber.Probe[EventEnvelope]] =
      JavaSink.create[EventEnvelope](system)
    val tp = readJournal.eventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).runWith(sink, mat)
    tp.within(within)(f(tp))
  }

  def withCurrentEventsByTag(within: FiniteDuration)(tag: String, offset: Offset)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val sink: akka.stream.javadsl.Sink[EventEnvelope, TestSubscriber.Probe[EventEnvelope]] =
      JavaSink.create[EventEnvelope](system)
    val tp = readJournal.currentEventsByTag(tag, offset).runWith(sink, mat)
    tp.within(within)(f(tp))
  }

  def withEventsByTag(within: FiniteDuration)(tag: String, offset: Offset)(
      f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val sink: akka.stream.javadsl.Sink[EventEnvelope, TestSubscriber.Probe[EventEnvelope]] =
      JavaSink.create[EventEnvelope](system)
    val tp = readJournal.eventsByTag(tag, offset).runWith(sink, mat)
    tp.within(within)(f(tp))
  }

  override def countJournal: Future[Long] =
    readJournal
      .currentPersistenceIds()
      .asScala
      .filter(pid => (1 to 3).map(id => s"my-$id").contains(pid))
      .mapAsync(1) { pid =>
        readJournal
          .currentEventsByPersistenceId(pid, 0, Long.MaxValue)
          .asScala
          .map(_ => 1L)
          .runFold(List.empty[Long])(_ :+ _)
          .map(_.sum)
      }
      .runFold(List.empty[Long])(_ :+ _)
      .map(_.sum)
}

object QueryTestSpec {
  implicit final class EventEnvelopeProbeOps(val probe: TestSubscriber.Probe[EventEnvelope]) extends AnyVal {
    def expectNextEventEnvelope(
        persistenceId: String,
        sequenceNr: Long,
        event: Any): TestSubscriber.Probe[EventEnvelope] = {
      val env = probe.expectNext()
      assertEnvelope(env, persistenceId, sequenceNr, event)
      probe
    }

    def expectNextEventEnvelope(
        timeout: FiniteDuration,
        persistenceId: String,
        sequenceNr: Long,
        event: Any): TestSubscriber.Probe[EventEnvelope] = {
      val env = probe.expectNext(timeout)
      assertEnvelope(env, persistenceId, sequenceNr, event)
      probe
    }

    private def assertEnvelope(env: EventEnvelope, persistenceId: String, sequenceNr: Long, event: Any): Unit = {
      assert(
        env.persistenceId == persistenceId,
        s"expected persistenceId $persistenceId, found ${env.persistenceId}, in $env")
      assert(env.sequenceNr == sequenceNr, s"expected sequenceNr $sequenceNr, found ${env.sequenceNr}, in $env")
      assert(env.event == event, s"expected event $event, found ${env.event}, in $env")
    }
  }
}

abstract class QueryTestSpec(config: String, configOverrides: Map[String, ConfigValue] = Map.empty)
    extends SingleActorSystemPerTestSpec(config, configOverrides) {
  case class DeleteCmd(toSequenceNr: Long = Long.MaxValue) extends Serializable

  final val ExpectNextTimeout = 10.second

  class TestActor(id: Int, replyToMessages: Boolean) extends PersistentActor with Stash {
    override val persistenceId: String = "my-" + id

    var state: Int = 0

    override def receiveCommand: Receive = idle

    def idle: Receive =
      LoggingReceive {
        case "state" =>
          sender() ! state

        case DeleteCmd(toSequenceNr) =>
          deleteMessages(toSequenceNr)
          if (replyToMessages) {
            context.become(awaitingDeleting(sender()))
          }

        case event: Int =>
          persist(event) { (event: Int) =>
            updateState(event)
            if (replyToMessages) sender() ! akka.actor.Status.Success(event)
          }

        case event @ Tagged(payload: Int, tags) =>
          persist(event) { _ =>
            updateState(payload)
            if (replyToMessages) sender() ! akka.actor.Status.Success((payload, tags))
          }
        case event: Event =>
          persist(event) { evt =>
            if (replyToMessages) sender() ! akka.actor.Status.Success(evt)
          }

        case event @ TaggedEvent(payload: Event, tag) =>
          persist(event) { _ =>
            if (replyToMessages) sender() ! akka.actor.Status.Success((payload, tag))
          }
        case event @ TaggedAsyncEvent(payload: Event, tag) =>
          persistAsync(event) { _ =>
            if (replyToMessages) sender() ! akka.actor.Status.Success((payload, tag))
          }
      }

    def awaitingDeleting(origSender: ActorRef): Receive =
      LoggingReceive {
        case DeleteMessagesSuccess(toSequenceNr) =>
          origSender ! s"deleted-$toSequenceNr"
          unstashAll()
          context.become(idle)

        case DeleteMessagesFailure(ex, _) =>
          origSender ! Status.Failure(ex)
          unstashAll()
          context.become(idle)

        // stash whatever other messages
        case _ => stash()
      }

    def updateState(event: Int): Unit = {
      state = state + event
    }

    override def receiveRecover: Receive =
      LoggingReceive { case event: Int =>
        updateState(event)
      }
  }

  def setupEmpty(persistenceId: Int, replyToMessages: Boolean)(implicit system: ActorSystem): ActorRef = {
    system.actorOf(Props(new TestActor(persistenceId, replyToMessages)))
  }

  def withTestActors(seq: Int = 1, replyToMessages: Boolean = false)(f: (ActorRef, ActorRef, ActorRef) => Unit)(
      implicit system: ActorSystem): Unit = {
    val refs = (seq until seq + 3).map(setupEmpty(_, replyToMessages)).toList
    try {
      expectAllStarted(refs)
      f(refs.head, refs.drop(1).head, refs.drop(2).head)
    } finally killActors(refs: _*)
  }

  def withManyTestActors(amount: Int, seq: Int = 1, replyToMessages: Boolean = false)(f: Seq[ActorRef] => Unit)(
      implicit system: ActorSystem): Unit = {
    val refs = (seq until seq + amount).map(setupEmpty(_, replyToMessages)).toList
    try {
      expectAllStarted(refs)
      f(refs)
    } finally killActors(refs: _*)
  }

  def expectAllStarted(refs: Seq[ActorRef])(implicit system: ActorSystem): Unit = {
    // make sure we notice early if the actors failed to start (because of issues with journal) makes debugging
    // failing tests easier as we know it is not the actual interaction from the test that is the problem
    implicit val ec: ExecutionContext = system.dispatcher
    Future.sequence(refs.map(_ ? "state")).futureValue
  }

  def withTags(payload: Any, tags: String*) = Tagged(payload, Set(tags: _*))

}

trait PostgresCleaner extends QueryTestSpec {

  def clearPostgres(): Unit =
    tables.foreach { name => withStatement(stmt => stmt.executeUpdate(s"DELETE FROM $name")) }

  override def beforeAll(): Unit = {
    dropAndCreate(Postgres)
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    dropAndCreate(Postgres)
    super.beforeEach()
  }
}

trait MysqlCleaner extends QueryTestSpec {

  def clearMySQL(): Unit = {
    withStatement { stmt =>
      stmt.execute("SET FOREIGN_KEY_CHECKS = 0")
      tables.foreach { name => stmt.executeUpdate(s"TRUNCATE $name") }
      stmt.execute("SET FOREIGN_KEY_CHECKS = 1")
    }
  }

  override def beforeAll(): Unit = {
    dropAndCreate(MySQL)
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    clearMySQL()
    super.beforeEach()
  }
}

trait OracleCleaner extends QueryTestSpec {

  def clearOracle(): Unit = {
    tables.foreach { name =>
      withStatement(stmt => stmt.executeUpdate(s"""DELETE FROM "$name" """))
    }
    withStatement(stmt => stmt.executeUpdate("""BEGIN "reset_sequence"; END; """))
  }

  override def beforeAll(): Unit = {
    dropAndCreate(Oracle)
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    clearOracle()
    super.beforeEach()
  }
}

trait SqlServerCleaner extends QueryTestSpec {

  var initial = true

  def clearSqlServer(): Unit = {
    val reset = if (initial) {
      initial = false
      1
    } else {
      0
    }
    withStatement { stmt =>
      tables.foreach { name => stmt.executeUpdate(s"DELETE FROM $name") }
      stmt.executeUpdate(s"DBCC CHECKIDENT('${journalTableName}', RESEED, $reset)")
    }
  }

  override def beforeAll(): Unit = {
    dropAndCreate(SqlServer)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    dropAndCreate(SqlServer)
    super.afterAll()
  }

  override def beforeEach(): Unit = {
    clearSqlServer()
    super.beforeEach()
  }
}

trait H2Cleaner extends QueryTestSpec {

  def clearH2(): Unit =
    tables.foreach { name => withStatement(stmt => stmt.executeUpdate(s"DELETE FROM $name")) }

  override def beforeEach(): Unit = {
    dropAndCreate(H2)
    super.beforeEach()
  }
}
