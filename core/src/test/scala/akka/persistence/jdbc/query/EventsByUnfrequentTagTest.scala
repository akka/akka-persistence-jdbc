/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.query

import akka.pattern.ask
import akka.persistence.jdbc.query.EventsByUnfrequentTagTest._
import akka.persistence.query.{ EventEnvelope, NoOffset, Sequence }
import com.typesafe.config.{ ConfigValue, ConfigValueFactory }

import scala.concurrent.duration._

object EventsByUnfrequentTagTest {
  val maxBufferSize = 20
  val refreshInterval = 500.milliseconds

  val configOverrides: Map[String, ConfigValue] = Map(
    "jdbc-read-journal.events-by-tag-buffer-sizes-per-query" -> ConfigValueFactory.fromAnyRef(1.toString),
    "jdbc-read-journal.max-buffer-size" -> ConfigValueFactory.fromAnyRef(maxBufferSize.toString),
    "jdbc-read-journal.refresh-interval" -> ConfigValueFactory.fromAnyRef(refreshInterval.toString()))
}

abstract class EventsByUnfrequentTagTest(config: String) extends QueryTestSpec(config, configOverrides) {

  final val NoMsgTime: FiniteDuration = 100.millis
  it should "persist and find a tagged event with multiple (frequently and unfrequently) tags" in withActorSystem {
    implicit system =>

      val journalOps = new ScalaJdbcReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, _, _) =>
        val often = "often"
        val notOften = "not-often"
        withClue("Persisting multiple tagged events") {
          (0 until 100).foreach { i =>
            val additional = if (i % 40 == 0) {
              Seq(notOften)
            } else Seq.empty
            val tags = Seq(often) ++ additional
            (actor1 ? withTags(1, tags: _*)).futureValue
          }

          eventually {
            journalOps.countJournal.futureValue shouldBe 100
          }
          journalOps.withEventsByTag()(often, NoOffset) { tp =>
            tp.request(Int.MaxValue)
            (1 to 100).foreach { i =>
              tp.expectNextPF { case EventEnvelope(Sequence(`i`), _, _, _) => }
            }
            tp.cancel()
            tp.expectNoMessage(NoMsgTime)
          }

          journalOps.withEventsByTag(10.seconds)(notOften, NoOffset) { tp =>
            tp.request(Int.MaxValue)
            tp.expectNextPF { case EventEnvelope(Sequence(1), _, _, _) => }
            tp.expectNextPF { case EventEnvelope(Sequence(41), _, _, _) => }
            tp.expectNextPF { case EventEnvelope(Sequence(81), _, _, _) => }

            tp.cancel()
            tp.expectNoMessage(NoMsgTime)
          }
        }

      }
  }

}

class H2ScalaEventsByUnfrequentTagTest extends EventsByUnfrequentTagTest("h2-shared-db-application.conf") with H2Cleaner
