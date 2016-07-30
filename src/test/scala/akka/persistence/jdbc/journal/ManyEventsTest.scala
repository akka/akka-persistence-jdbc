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

package akka.persistence.jdbc.journal

import akka.NotUsed
import akka.actor.{ ActorRef, Props }
import akka.persistence.jdbc.TestSpec
import akka.persistence.jdbc.journal.ManyEventsTest.{ Person, Took }
import akka.persistence.jdbc.util.Schema._
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl.EventWriter.WriteEvent
import akka.persistence.query.scaladsl.{ CurrentEventsByPersistenceIdQuery, CurrentEventsByTagQuery, EventWriter, ReadJournal }
import akka.persistence.{ PersistentActor, PersistentRepr, RecoveryCompleted }
import akka.stream.FlowShape
import akka.stream.scaladsl.extension.Implicits._
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Sink, Source }
import akka.testkit.TestProbe
import com.typesafe.config.{ Config, ConfigFactory }

import scala.collection.immutable._
import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.duration._

object ManyEventsTest {
  final case class Person(name: String, age: Int)
  final case class Took(start: Long, recoveryTime: Long, lastSeqNr: Long)

  final val TenThousand = 10000
  final val HundredThousand = 100000
}

abstract class ManyEventsTest(numOfEvents: Int, config: Config, schemaType: SchemaType) extends TestSpec(config) {
  lazy val journal = PersistenceQuery(system).readJournalFor("jdbc-read-journal")
    .asInstanceOf[ReadJournal with EventWriter with CurrentEventsByPersistenceIdQuery with CurrentEventsByTagQuery]

  final val ReceiveTimeout = 5.minutes

  it should s"load $numOfEvents events" in {
    val pid = "pid1"
    storeEvents(pid, numOfEvents).toTry should be a 'success
    withActor(pid) { ref => tp =>
      tp.send(ref, 0)
      val took = tp.receiveOne(ReceiveTimeout).asInstanceOf[Took]
      val duration = took.recoveryTime - took.start
      log.info("{}, duration: {} ms", took, duration)
    }
  }

  def storeEvents(pid: String, num: Int): Future[Long] = {
    val start = Platform.currentTime
    Source.cycle(() => (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).iterator)
      .map(_.toString)
      .zip(Source.cycle(() => (1 to 100).iterator))
      .take(num)
      .via(broadcastAndLog())
      .map(Person.tupled)
      .zipWithIndex.map { case (person, seqNr) => WriteEvent(PersistentRepr(person, seqNr, pid), Set.empty[String]) }
      .via(journal.eventWriter)
      .runWith(Sink.ignore)
      .map(_ => Platform.currentTime - start)
  }

  def withActor(pid: String)(f: ActorRef => TestProbe => Unit): Unit = {
    val actor = system.actorOf(Props(new PersistentActor {
      override val persistenceId: String = pid
      val start: Long = Platform.currentTime
      var recoveryTime: Long = 0

      override val receiveRecover: Receive = {
        case RecoveryCompleted =>
          recoveryTime = Platform.currentTime
      }

      override val receiveCommand: Receive = {
        case _ => sender() ! Took(start, recoveryTime, lastSequenceNr)
      }
    }))
    try f(actor)(TestProbe()) finally killActors(actor)
  }

  def broadcastAndLog[A](each: Long = 1000): Flow[A, A, NotUsed] = Flow.fromGraph[A, A, NotUsed](GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._
    val logFlow = Flow[A].statefulMapConcat { () =>
      var last = Platform.currentTime
      var num = 0L
      (x: A) =>
        num += 1
        if (num % each == 0) {
          val duration = Platform.currentTime - last
          log.info("[{} ms / {}]: {}", duration, each, num)
          last = Platform.currentTime
        }
        Iterable(x)
    }
    val bcast = b.add(Broadcast[A](2, false))
    bcast ~> logFlow ~> Sink.ignore
    FlowShape.of(bcast.in, bcast.out(1))
  })

  override def beforeAll(): Unit = {
    dropCreate(schemaType)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    db.close()
    super.afterAll()
  }
}

class PostgresRecoverFromTenThousandEventsTest extends ManyEventsTest(ManyEventsTest.TenThousand, ConfigFactory.load("postgres-application.conf"), Postgres())

class MySQLRecoverFromTenThousandEventsTest extends ManyEventsTest(ManyEventsTest.TenThousand, ConfigFactory.load("mysql-application.conf"), MySQL())

class OracleRecoverFromTenThousandEventsTest extends ManyEventsTest(ManyEventsTest.TenThousand, ConfigFactory.load("oracle-application.conf"), Oracle())

class H2RecoverFromTenThousandEventsTest extends ManyEventsTest(ManyEventsTest.TenThousand, ConfigFactory.load("h2-application.conf"), H2())