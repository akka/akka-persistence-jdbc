/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.query

import akka.persistence.query.{ EventEnvelope, NoOffset, Sequence }
import akka.pattern._
import com.typesafe.config.ConfigFactory
import org.scalactic.source.Position

import scala.concurrent.duration._
import org.scalatest.matchers.should.Matchers

abstract class HardDeleteQueryTest(config: String) extends QueryTestSpec(config) with Matchers {
  implicit val askTimeout = 500.millis

  it should "not return deleted events when using CurrentEventsByTag" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, _, _) =>
      (actor1 ? withTags(1, "number")).futureValue
      (actor1 ? withTags(2, "number")).futureValue
      (actor1 ? withTags(3, "number")).futureValue

      // delete all three events and wait for confirmations
      (actor1 ? DeleteCmd(1)).futureValue shouldBe "deleted-1"
      (actor1 ? DeleteCmd(2)).futureValue shouldBe "deleted-2"
      (actor1 ? DeleteCmd(3)).futureValue shouldBe "deleted-3"

      // check that nothing gets delivered
      journalOps.withCurrentEventsByTag()("number", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectComplete()
      }
    }
  }

  it should "not return deleted events when using EventsByTag" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, _, _) =>
      (actor1 ? withTags(1, "number")).futureValue
      (actor1 ? withTags(2, "number")).futureValue
      (actor1 ? withTags(3, "number")).futureValue

      // delete all three events and wait for confirmations
      (actor1 ? DeleteCmd(1)).futureValue shouldBe "deleted-1"
      (actor1 ? DeleteCmd(2)).futureValue shouldBe "deleted-2"
      (actor1 ? DeleteCmd(3)).futureValue shouldBe "deleted-3"

      // check that nothing gets delivered
      journalOps.withEventsByTag()("number", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.cancel()
      }
    }
  }

  it should "not return deleted events when using CurrentEventsByPersistenceId" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, _, _) =>
      (actor1 ? withTags(1, "number")).futureValue
      (actor1 ? withTags(2, "number")).futureValue
      (actor1 ? withTags(3, "number")).futureValue

      // delete all three events and wait for confirmations
      (actor1 ? DeleteCmd(1)).futureValue shouldBe "deleted-1"
      (actor1 ? DeleteCmd(2)).futureValue shouldBe "deleted-2"
      (actor1 ? DeleteCmd(3)).futureValue shouldBe "deleted-3"

      // check that nothing gets delivered
      journalOps.withCurrentEventsByPersistenceId()("my-1") { tp =>
        tp.request(Int.MaxValue)
        tp.expectComplete()
      }
    }
  }

  it should "not return deleted events when using EventsByPersistenceId" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, _, _) =>
      (actor1 ? withTags(1, "number")).futureValue
      (actor1 ? withTags(2, "number")).futureValue
      (actor1 ? withTags(3, "number")).futureValue

      // delete all three events and wait for confirmations
      (actor1 ? DeleteCmd(1)).futureValue shouldBe "deleted-1"
      (actor1 ? DeleteCmd(2)).futureValue shouldBe "deleted-2"
      (actor1 ? DeleteCmd(3)).futureValue shouldBe "deleted-3"

      // check that nothing gets delivered
      journalOps.withEventsByPersistenceId()("my-1") { tp =>
        tp.request(Int.MaxValue)
        tp.cancel()
      }
    }
  }
}

class H2HardDeleteQueryTest extends HardDeleteQueryTest("h2-application-with-hard-delete.conf") with H2Cleaner
