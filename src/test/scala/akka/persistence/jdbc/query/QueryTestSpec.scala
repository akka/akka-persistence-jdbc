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

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.LoggingReceive
import akka.persistence.PersistentActor
import akka.persistence.jdbc.TestSpec
import akka.persistence.jdbc.query.EventAdapterTest.{Event, TaggedEvent}
import akka.persistence.jdbc.query.JournalSequenceActor.{GetMaxOrderingId, MaxOrderingId}
import akka.persistence.jdbc.query.javadsl.{JdbcReadJournal => JavaJdbcReadJournal}
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.journal.Tagged
import akka.persistence.query.{EventEnvelope, PersistenceQuery, Sequence}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.javadsl.{TestSink => JavaSink}
import akka.stream.testkit.scaladsl.TestSink
import akka.util.Timeout
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}

trait ReadJournalOperations {
  def withCurrentPersistenceIds(within: FiniteDuration = 60.second)(f: TestSubscriber.Probe[String] => Unit): Unit
  def withPersistenceIds(within: FiniteDuration = 60.second)(f: TestSubscriber.Probe[String] => Unit): Unit
  def withCurrentEventsByPersistenceId(within: FiniteDuration = 60.second)(persistenceId: String, fromSequenceNr: Long = 0, toSequenceNr: Long = Long.MaxValue)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit
  def withEventsByPersistenceId(within: FiniteDuration = 60.second)(persistenceId: String, fromSequenceNr: Long = 0, toSequenceNr: Long = Long.MaxValue)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit
  def withCurrentEventsByTag(within: FiniteDuration = 60.second)(tag: String, offset: Long)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit
  def withEventsByTag(within: FiniteDuration = 60.second)(tag: String, offset: Long)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit
  def countJournal: Future[Long]
}

trait ScalaJdbcReadJournalOperations extends ReadJournalOperations {
  implicit def system: ActorSystem

  implicit def mat: Materializer

  implicit def ec: ExecutionContext

  lazy val readJournal: JdbcReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

  def withCurrentPersistenceIds(within: FiniteDuration)(f: TestSubscriber.Probe[String] => Unit): Unit = {
    val tp = readJournal.currentPersistenceIds().runWith(TestSink.probe[String])
    tp.within(within)(f(tp))
  }

  def withPersistenceIds(within: FiniteDuration)(f: TestSubscriber.Probe[String] => Unit): Unit = {
    val tp = readJournal.persistenceIds().runWith(TestSink.probe[String])
    tp.within(within)(f(tp))
  }

  def withCurrentEventsByPersistenceId(within: FiniteDuration)(persistenceId: String, fromSequenceNr: Long = 0, toSequenceNr: Long = Long.MaxValue)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).runWith(TestSink.probe[EventEnvelope])
    tp.within(within)(f(tp))
  }

  def withEventsByPersistenceId(within: FiniteDuration)(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.eventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).runWith(TestSink.probe[EventEnvelope])
    tp.within(within)(f(tp))
  }

  def withCurrentEventsByTag(within: FiniteDuration)(tag: String, offset: Long)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.currentEventsByTag(tag, offset).runWith(TestSink.probe[EventEnvelope])
    tp.within(within)(f(tp))
  }

  def withEventsByTag(within: FiniteDuration)(tag: String, offset: Long)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.eventsByTag(tag, offset).runWith(TestSink.probe[EventEnvelope])
    tp.within(within)(f(tp))
  }

  override def countJournal: Future[Long] =
    readJournal.currentPersistenceIds()
      .filter(pid => (1 to 3).map(id => s"my-$id").contains(pid))
      .mapAsync(1) { pid =>
        readJournal.currentEventsByPersistenceId(pid, 0, Long.MaxValue).map(_ => 1L).runWith(Sink.seq).map(_.sum)
      }.runWith(Sink.seq).map(_.sum)

  def latestOrdering: Future[Long] = {
    import akka.pattern.ask
    implicit val askTimeout = Timeout(100.millis)
    readJournal.orderingActor.ask(GetMaxOrderingId)
      .mapTo[MaxOrderingId]
      .map(_.maxOrdering)
  }
}

trait JavaDslJdbcReadJournalOperations extends ReadJournalOperations {
  implicit def system: ActorSystem

  implicit def mat: Materializer

  implicit def ec: ExecutionContext

  lazy val readJournal = PersistenceQuery.get(system).getReadJournalFor(classOf[javadsl.JdbcReadJournal], JavaJdbcReadJournal.Identifier)

  def withCurrentPersistenceIds(within: FiniteDuration = 1.second)(f: TestSubscriber.Probe[String] => Unit): Unit = {
    val tp = readJournal.currentPersistenceIds().runWith(JavaSink.probe(system), mat)
    tp.within(within)(f(tp))
  }

  def withPersistenceIds(within: FiniteDuration = 1.second)(f: TestSubscriber.Probe[String] => Unit): Unit = {
    val tp = readJournal.persistenceIds().runWith(JavaSink.probe(system), mat)
    tp.within(within)(f(tp))
  }

  def withCurrentEventsByPersistenceId(within: FiniteDuration = 1.second)(persistenceId: String, fromSequenceNr: Long = 0, toSequenceNr: Long = Long.MaxValue)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).runWith(JavaSink.probe(system), mat)
    tp.within(within)(f(tp))
  }

  def withEventsByPersistenceId(within: FiniteDuration = 1.second)(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.eventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).runWith(JavaSink.probe(system), mat)
    tp.within(within)(f(tp))
  }

  def withCurrentEventsByTag(within: FiniteDuration = 1.second)(tag: String, offset: Long)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.currentEventsByTag(tag, Sequence(offset)).runWith(JavaSink.probe(system), mat)
    tp.within(within)(f(tp))
  }

  def withEventsByTag(within: FiniteDuration = 1.second)(tag: String, offset: Long)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.eventsByTag(tag, Sequence(offset)).runWith(JavaSink.probe(system), mat)
    tp.within(within)(f(tp))
  }

  override def countJournal: Future[Long] =
    readJournal.currentPersistenceIds().asScala
      .filter(pid => (1 to 3).map(id => s"my-$id").contains(pid))
      .mapAsync(1) { pid =>
        readJournal.currentEventsByPersistenceId(pid, 0, Long.MaxValue).asScala.map(_ => 1L).runFold(List.empty[Long])(_ :+ _).map(_.sum)
      }.runFold(List.empty[Long])(_ :+ _)
      .map(_.sum)
}

abstract class QueryTestSpec(config: String) extends TestSpec(config) with ReadJournalOperations {

  case class DeleteCmd(toSequenceNr: Long = Long.MaxValue) extends Serializable

  final val ExpectNextTimeout = 10.second

  class TestActor(id: Int) extends PersistentActor {
    override val persistenceId: String = "my-" + id

    var state: Int = 0

    override def receiveCommand: Receive = LoggingReceive {
      case "state" =>
        sender() ! state

      case DeleteCmd(toSequenceNr) =>
        deleteMessages(toSequenceNr)
        sender() ! s"deleted-$toSequenceNr"

      case event: Int =>
        persist(event) { (event: Int) =>
          updateState(event)
          sender() ! akka.actor.Status.Success(event)
        }

      case event @ Tagged(payload: Int, tags) =>
        persist(event) { (event: Tagged) =>
          updateState(payload)
          sender() ! akka.actor.Status.Success((payload, tags))
        }
      case event: Event =>
        persist(event) { evt =>
          sender() ! akka.actor.Status.Success(evt)
        }

      case event @ TaggedEvent(payload: Event, tag) =>
        persist(event) { evt =>
          sender() ! akka.actor.Status.Success((payload, tag))
        }
    }

    def updateState(event: Int): Unit = {
      state = state + event
    }

    override def receiveRecover: Receive = LoggingReceive {
      case event: Int => updateState(event)
    }
  }

  def setupEmpty(persistenceId: Int): ActorRef = {
    system.actorOf(Props(new TestActor(persistenceId)))
  }

  def withTestActors(seq: Int = 1)(f: (ActorRef, ActorRef, ActorRef) => Unit): Unit = {
    val refs = (seq until seq + 3).map(setupEmpty).toList
    try f(refs.head, refs.drop(1).head, refs.drop(2).head) finally killActors(refs: _*)
  }

  def withManyTestActors(amount: Int, seq: Int = 1)(f: Seq[ActorRef] => Unit): Unit = {
    val refs = (seq until seq + amount).map(setupEmpty).toList
    try f(refs) finally killActors(refs: _*)
  }

  def withTags(payload: Any, tags: String*) = Tagged(payload, Set(tags: _*))

  override protected def afterAll(): Unit = {
    db.close()
    system.terminate().toTry should be a 'success
  }
}

trait PostgresCleaner extends QueryTestSpec {
  import akka.persistence.jdbc.util.Schema.Postgres

  val actionsClearPostgres = (for {
    _ <- sqlu"""TRUNCATE journal"""
    _ <- sqlu"""TRUNCATE snapshot"""
  } yield ()).transactionally

  def clearPostgres(): Unit =
    withDatabase(_.run(actionsClearPostgres).toTry) should be a 'success

  override def beforeAll() = {
    dropCreate(Postgres())
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    dropCreate(Postgres())
    super.beforeEach()
  }

  override def afterAll(): Unit = {
    clearPostgres()
    super.afterAll()
  }
}

trait MysqlCleaner extends QueryTestSpec {
  import akka.persistence.jdbc.util.Schema.MySQL

  val actionsClearMySQL = (for {
    _ <- sqlu"""TRUNCATE journal"""
    _ <- sqlu"""TRUNCATE snapshot"""
  } yield ()).transactionally

  def clearMySQL(): Unit =
    withDatabase(_.run(actionsClearMySQL).toTry) should be a 'success

  override def beforeAll() = {
    dropCreate(MySQL())
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    clearMySQL()
    super.beforeEach()
  }

  override def afterAll(): Unit = {
    clearMySQL()
    super.afterAll()
  }
}

trait OracleCleaner extends QueryTestSpec {
  import akka.persistence.jdbc.util.Schema.Oracle

  val actionsClearOracle = (for {
    _ <- sqlu"""DELETE FROM "journal""""
    _ <- sqlu"""DELETE FROM "snapshot""""
    _ <- sqlu"""BEGIN "reset_sequence"; END; """
  } yield ()).transactionally

  def clearOracle(): Unit =
    withDatabase(_.run(actionsClearOracle).toTry) should be a 'success

  override def beforeAll() = {
    dropCreate(Oracle())
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    dropOracle()
    super.afterAll()
  }

  override def beforeEach(): Unit = {
    clearOracle()
    super.beforeEach()
  }
}

trait H2Cleaner extends QueryTestSpec {
  import akka.persistence.jdbc.util.Schema.H2

  val actionsClearH2 = (for {
    _ <- sqlu"""TRUNCATE TABLE journal"""
    _ <- sqlu"""TRUNCATE TABLE snapshot"""
  } yield ()).transactionally

  def clearH2(): Unit =
    withDatabase(_.run(actionsClearH2).toTry) should be a 'success

  override def beforeAll() = {
    dropCreate(H2())
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    clearH2()
    super.afterAll()
  }

  override def beforeEach(): Unit = {
    dropCreate(H2())
    super.beforeEach()
  }
}