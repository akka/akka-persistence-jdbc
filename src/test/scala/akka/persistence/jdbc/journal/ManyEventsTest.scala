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

import akka.actor.{ ActorRef, Props }
import akka.persistence.jdbc.TestSpec
import akka.persistence.jdbc.journal.ManyEventsTest.Person
import akka.persistence.jdbc.util.Schema.{ Postgres, SchemaType }
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl.EventWriter.WriteEvent
import akka.persistence.query.scaladsl.{ CurrentEventsByPersistenceIdQuery, CurrentEventsByTagQuery, EventWriter, ReadJournal }
import akka.persistence.{ PersistentActor, PersistentRepr, RecoveryCompleted }
import akka.stream.scaladsl.extension.Implicits._
import akka.stream.scaladsl.{ Sink, Source }
import akka.testkit.TestProbe
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.Ignore

import scala.collection.immutable._
import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.duration._

object ManyEventsTest {
  final case class Person(name: String, age: Int)
}

abstract class ManyEventsTest(config: Config, schemaType: SchemaType) extends TestSpec(config) {
  lazy val journal = PersistenceQuery(system).readJournalFor("jdbc-read-journal")
    .asInstanceOf[ReadJournal with EventWriter with CurrentEventsByPersistenceIdQuery with CurrentEventsByTagQuery]

  final val TenThousand = 10000
  final val HundredThousand = 100000
  final val OneMillion = 1000000
  final val FiveMillion = 5000000
  final val ReceiveTimeout = 1.hour

  it should s"load $TenThousand events" in {
    val pid = "pid1"
    storeEvents(pid, TenThousand).toTry should be a 'success
    withActor(pid) { ref => tp =>
      tp.send(ref, 0)
      println(tp.receiveOne(ReceiveTimeout))
    }
  }

  it should s"load $HundredThousand events" in {
    val pid = "pid2"
    storeEvents(pid, HundredThousand).toTry should be a 'success
    withActor(pid) { ref => tp =>
      tp.send(ref, 0)
      println(tp.receiveOne(ReceiveTimeout))
    }
  }

  it should s"load $OneMillion events" in {
    val pid = "pid3"
    storeEvents(pid, OneMillion).toTry should be a 'success
    withActor(pid) { ref => tp =>
      tp.send(ref, 0)
      println(tp.receiveOne(ReceiveTimeout))
    }
  }

  it should s"load $FiveMillion events" in {
    val pid = "pid4"
    storeEvents(pid, FiveMillion).toTry should be a 'success
    withActor(pid) { ref => tp =>
      tp.send(ref, 0)
      println(tp.receiveOne(ReceiveTimeout))
    }
  }

  def storeEvents(pid: String, num: Int): Future[Long] = {
    val start = Platform.currentTime
    Source.zipWithN[Any, (String, Int)] { case Seq(a: String, b: Int) => (a, b) }(Seq(
      Source.cycle(() => (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).iterator).map(_.toString),
      Source.cycle(() => (1 to 100).iterator)
    )).take(num)
      .map(Person.tupled)
      .zipWithIndex.map { case (person, seqNr) => WriteEvent(PersistentRepr(person, seqNr, pid), Set.empty[String]) }
      .via(journal.eventWriter)
      .runWith(Sink.ignore)
      .map { _ =>
        val xx = Platform.currentTime - start
        println(f"Storing $num%d events took $xx ms")
        xx
      }
  }

  def withActor(pid: String)(f: ActorRef => TestProbe => Unit): Unit = {
    val actor = system.actorOf(Props(new PersistentActor {
      val start = Platform.currentTime
      var took: Long = 0
      override def receiveRecover: Receive = {
        case RecoveryCompleted =>
          took = Platform.currentTime - start
          println(s"$pid => completed, took: $took")
        case _: Person =>
        case evt       => println(s"$pid => recovering: $evt")
      }
      override def receiveCommand: Receive = {
        case _ => sender() ! akka.actor.Status.Success(s"start: $start, took: $took, lastSeqNr: $lastSequenceNr")
      }
      override def persistenceId: String = pid
    }))
    try f(actor)(TestProbe()) finally killActors(actor)
  }

  override def beforeAll(): Unit = {
    dropCreate(schemaType)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    db.close()
    super.afterAll()
  }
}

@Ignore
class PostgresManyEventsTest extends ManyEventsTest(ConfigFactory.load("postgres-application.conf"), Postgres())