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

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.event.LoggingReceive
import akka.persistence.PersistentActor
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
import akka.stream.{ ActorMaterializer, Materializer }
import com.typesafe.config.ConfigValue
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future
import scala.concurrent.duration.{ FiniteDuration, _ }

trait ReadJournalOperations {
  def withCurrentPersistenceIds(within: FiniteDuration = 60.second)(f: TestSubscriber.Probe[String] => Unit): Unit
  def withPersistenceIds(within: FiniteDuration = 60.second)(f: TestSubscriber.Probe[String] => Unit): Unit
  def withCurrentEventsByPersistenceId(within: FiniteDuration = 60.second)(persistenceId: String, fromSequenceNr: Long = 0, toSequenceNr: Long = Long.MaxValue)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit
  def withEventsByPersistenceId(within: FiniteDuration = 60.second)(persistenceId: String, fromSequenceNr: Long = 0, toSequenceNr: Long = Long.MaxValue)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit
  def withCurrentEventsByTag(within: FiniteDuration = 60.second)(tag: String, offset: Offset)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit
  def withEventsByTag(within: FiniteDuration = 60.second)(tag: String, offset: Offset)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit
  def countJournal: Future[Long]
}

class ScalaJdbcReadJournalOperations(readJournal: JdbcReadJournal)(implicit system: ActorSystem, mat: Materializer) extends ReadJournalOperations {
  def this(system: ActorSystem) =
    this(PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier))(system, ActorMaterializer()(system))

  import system.dispatcher

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

  def withCurrentEventsByTag(within: FiniteDuration)(tag: String, offset: Offset)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.currentEventsByTag(tag, offset).runWith(TestSink.probe[EventEnvelope])
    tp.within(within)(f(tp))
  }

  def withEventsByTag(within: FiniteDuration)(tag: String, offset: Offset)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.eventsByTag(tag, offset).runWith(TestSink.probe[EventEnvelope])
    tp.within(within)(f(tp))
  }

  override def countJournal: Future[Long] =
    readJournal.currentPersistenceIds()
      .filter(pid => (1 to 3).map(id => s"my-$id").contains(pid))
      .mapAsync(1) { pid =>
        readJournal.currentEventsByPersistenceId(pid, 0, Long.MaxValue).map(_ => 1L).runWith(Sink.seq).map(_.sum)
      }.runWith(Sink.seq).map(_.sum)
}

class JavaDslJdbcReadJournalOperations(readJournal: javadsl.JdbcReadJournal)(implicit system: ActorSystem, mat: Materializer) extends ReadJournalOperations {
  def this(system: ActorSystem) =
    this(PersistenceQuery.get(system).getReadJournalFor(classOf[javadsl.JdbcReadJournal], JavaJdbcReadJournal.Identifier))(system, ActorMaterializer()(system))

  import system.dispatcher

  def withCurrentPersistenceIds(within: FiniteDuration)(f: TestSubscriber.Probe[String] => Unit): Unit = {
    val tp = readJournal.currentPersistenceIds().runWith(JavaSink.probe(system), mat)
    tp.within(within)(f(tp))
  }

  def withPersistenceIds(within: FiniteDuration)(f: TestSubscriber.Probe[String] => Unit): Unit = {
    val tp = readJournal.persistenceIds().runWith(JavaSink.probe(system), mat)
    tp.within(within)(f(tp))
  }

  def withCurrentEventsByPersistenceId(within: FiniteDuration)(persistenceId: String, fromSequenceNr: Long = 0, toSequenceNr: Long = Long.MaxValue)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).runWith(JavaSink.probe(system), mat)
    tp.within(within)(f(tp))
  }

  def withEventsByPersistenceId(within: FiniteDuration)(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.eventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).runWith(JavaSink.probe(system), mat)
    tp.within(within)(f(tp))
  }

  def withCurrentEventsByTag(within: FiniteDuration)(tag: String, offset: Offset)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.currentEventsByTag(tag, offset).runWith(JavaSink.probe(system), mat)
    tp.within(within)(f(tp))
  }

  def withEventsByTag(within: FiniteDuration)(tag: String, offset: Offset)(f: TestSubscriber.Probe[EventEnvelope] => Unit): Unit = {
    val tp = readJournal.eventsByTag(tag, offset).runWith(JavaSink.probe(system), mat)
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

abstract class QueryTestSpec(config: String, configOverrides: Map[String, ConfigValue] = Map.empty) extends SingleActorSystemPerTestSpec(config, configOverrides) {

  case class DeleteCmd(toSequenceNr: Long = Long.MaxValue) extends Serializable

  final val ExpectNextTimeout = 10.second

  class TestActor(id: Int, replyToMessages: Boolean) extends PersistentActor {
    override val persistenceId: String = "my-" + id

    var state: Int = 0

    override def receiveCommand: Receive = LoggingReceive {
      case "state" =>
        sender() ! state

      case DeleteCmd(toSequenceNr) =>
        deleteMessages(toSequenceNr)
        if (replyToMessages) sender() ! s"deleted-$toSequenceNr"

      case event: Int =>
        persist(event) { (event: Int) =>
          updateState(event)
          if (replyToMessages) sender() ! akka.actor.Status.Success(event)
        }

      case event @ Tagged(payload: Int, tags) =>
        persist(event) { (event: Tagged) =>
          updateState(payload)
          if (replyToMessages) sender() ! akka.actor.Status.Success((payload, tags))
        }
      case event: Event =>
        persist(event) { evt =>
          if (replyToMessages) sender() ! akka.actor.Status.Success(evt)
        }

      case event @ TaggedEvent(payload: Event, tag) =>
        persist(event) { evt =>
          if (replyToMessages) sender() ! akka.actor.Status.Success((payload, tag))
        }
      case event @ TaggedAsyncEvent(payload: Event, tag) =>
        persistAsync(event) { evt =>
          if (replyToMessages) sender() ! akka.actor.Status.Success((payload, tag))
        }
    }

    def updateState(event: Int): Unit = {
      state = state + event
    }

    override def receiveRecover: Receive = LoggingReceive {
      case event: Int => updateState(event)
    }
  }

  def setupEmpty(persistenceId: Int, replyToMessages: Boolean)(implicit system: ActorSystem): ActorRef = {
    system.actorOf(Props(new TestActor(persistenceId, replyToMessages)))
  }

  def withTestActors(seq: Int = 1, replyToMessages: Boolean = false)(f: (ActorRef, ActorRef, ActorRef) => Unit)(implicit system: ActorSystem): Unit = {
    val refs = (seq until seq + 3).map(setupEmpty(_, replyToMessages)).toList
    try f(refs.head, refs.drop(1).head, refs.drop(2).head) finally killActors(refs: _*)
  }

  def withManyTestActors(amount: Int, seq: Int = 1, replyToMessages: Boolean = false)(f: Seq[ActorRef] => Unit)(implicit system: ActorSystem): Unit = {
    val refs = (seq until seq + amount).map(setupEmpty(_, replyToMessages)).toList
    try f(refs) finally killActors(refs: _*)
  }

  def withTags(payload: Any, tags: String*) = Tagged(payload, Set(tags: _*))
}

trait PostgresCleaner extends QueryTestSpec {
  import akka.persistence.jdbc.util.Schema.Postgres

  val actionsClearPostgres =
    DBIO.seq(
      sqlu"""TRUNCATE journal""",
      sqlu"""TRUNCATE snapshot""").transactionally

  def clearPostgres(): Unit =
    withDatabase(_.run(actionsClearPostgres).futureValue)

  override def beforeAll(): Unit = {
    dropCreate(Postgres())
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    dropCreate(Postgres())
    super.beforeEach()
  }
}

trait MysqlCleaner extends QueryTestSpec {
  import akka.persistence.jdbc.util.Schema.MySQL

  val actionsClearMySQL =
    DBIO.seq(
      sqlu"""TRUNCATE journal""",
      sqlu"""TRUNCATE snapshot""").transactionally

  def clearMySQL(): Unit =
    withDatabase(_.run(actionsClearMySQL).futureValue)

  override def beforeAll(): Unit = {
    dropCreate(MySQL())
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    clearMySQL()
    super.beforeEach()
  }
}

trait OracleCleaner extends QueryTestSpec {
  import akka.persistence.jdbc.util.Schema.Oracle

  val actionsClearOracle =
    DBIO.seq(
      sqlu"""DELETE FROM "journal"""",
      sqlu"""DELETE FROM "snapshot"""",
      sqlu"""BEGIN "reset_sequence"; END; """).transactionally

  def clearOracle(): Unit =
    withDatabase(_.run(actionsClearOracle).futureValue)

  override def beforeAll(): Unit = {
    dropCreate(Oracle())
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    clearOracle()
    super.beforeEach()
  }
}

trait H2Cleaner extends QueryTestSpec {
  import akka.persistence.jdbc.util.Schema.H2

  val actionsClearH2 =
    DBIO.seq(
      sqlu"""TRUNCATE TABLE journal""",
      sqlu"""TRUNCATE TABLE snapshot""").transactionally

  def clearH2(): Unit =
    withDatabase(_.run(actionsClearH2).futureValue)

  override def beforeEach(): Unit = {
    dropCreate(H2())
    super.beforeEach()
  }
}