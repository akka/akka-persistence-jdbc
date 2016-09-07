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
import akka.persistence.PersistentRepr
import akka.persistence.jdbc.TestSpec
import akka.persistence.jdbc.util.Schema._
import akka.persistence.query.scaladsl.EventWriter.WriteEvent
import akka.persistence.query.scaladsl.{ CurrentEventsByPersistenceIdQuery, CurrentEventsByTagQuery, EventWriter, ReadJournal }
import akka.persistence.query.{ EventEnvelope, PersistenceQuery }
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.scaladsl.TestSink
import com.typesafe.config.{ Config, ConfigFactory }
import akka.stream.scaladsl.extension.Implicits._

import scala.collection.immutable._

abstract class EventWriterTest(config: Config, schemaType: SchemaType) extends TestSpec(config) {
  lazy val journal = PersistenceQuery(system).readJournalFor("jdbc-read-journal")
    .asInstanceOf[ReadJournal with EventWriter with CurrentEventsByPersistenceIdQuery with CurrentEventsByTagQuery]

  def withTestProbe[A](src: Source[A, NotUsed])(f: TestSubscriber.Probe[A] ⇒ Unit): Unit =
    f(src.runWith(TestSink.probe(system)))

  lazy val chars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')

  final val Cycles = 1

  def result(pid: String, offset: Int = 0): Seq[EventEnvelope] =
    Source.cycle(() => chars.iterator).take(chars.size * Cycles).zipWithIndex.map {
      case (char, index) =>
        EventEnvelope(index + offset, pid, index, char)
    }.runWith(Sink.seq).futureValue

  it should "write events without tags" in {
    Source.cycle(() => chars.iterator).take(chars.size * Cycles).zipWithIndex.map {
      case (pl, seqNr) =>
        WriteEvent(PersistentRepr(pl, seqNr, "foo"), Set.empty[String])
    }.via(journal.eventWriter).runWith(Sink.ignore).toTry should be a 'success

    withTestProbe(journal.currentEventsByPersistenceId("foo", 0, Long.MaxValue)) { tp =>
      tp.request(Long.MaxValue)
      tp.expectNextN(result("foo"))
      tp.expectComplete()
    }

    withTestProbe(journal.currentEventsByPersistenceId("foobar", 0, Long.MaxValue)) { tp =>
      tp.request(Long.MaxValue)
      tp.expectComplete()
    }
  }

  it should "write events with tags" in {
    Source.cycle(() => chars.iterator).take(chars.size * Cycles).zipWithIndex.map {
      case (pl, seqNr) =>
        WriteEvent(PersistentRepr(pl, seqNr, "foobar"), Set("bar"))
    }.via(journal.eventWriter).runWith(Sink.ignore).toTry should be a 'success

    withTestProbe(journal.currentEventsByTag("bar", 0)) { tp =>
      tp.request(Long.MaxValue)
      tp.expectNextN(result("foobar", result("foobar", Cycles).size))
      tp.expectComplete()
    }

    withTestProbe(journal.currentEventsByTag("unknown", 0)) { tp =>
      tp.request(Long.MaxValue)
      tp.expectComplete()
    }
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

class PostgresEventWriterTest extends EventWriterTest(ConfigFactory.load("postgres-application.conf"), Postgres())

class MySQLEventWriterTest extends EventWriterTest(ConfigFactory.load("mysql-application.conf"), MySQL())

class OracleEventWriterTest extends EventWriterTest(ConfigFactory.load("oracle-application.conf"), Oracle())

class H2EventWriterTest extends EventWriterTest(ConfigFactory.load("h2-application.conf"), H2())
