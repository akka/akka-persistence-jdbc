/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2025 Lightbend Inc. <https://akka.io>
 */

package akka.persistence.jdbc.cleanup.scaladsl

import akka.persistence.jdbc.query.{ H2Cleaner, QueryTestSpec }
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import akka.pattern.ask
import akka.persistence.jdbc.query.EventAdapterTest.Snapshot

abstract class EventSourcedCleanupTest(config: String) extends QueryTestSpec(config) with Matchers {
  implicit val askTimeout: FiniteDuration = 500.millis

  it should "delete all events and reset sequence number" in withActorSystem { implicit system =>
    withTestActors(replyToMessages = true) { (actor1, _, _) =>
      (actor1 ? 1).futureValue
      (actor1 ? 2).futureValue
      (actor1 ? 3).futureValue
    }
    new EventSourcedCleanup(system).deleteAllEvents("my-1", true).futureValue
    withTestActors(replyToMessages = true) { (actor1, _, _) =>
      (actor1 ? "state").futureValue.asInstanceOf[Int] shouldBe 0
    }
  }

  it should "delete snapshots as well as events" in withActorSystem { implicit system =>
    withTestActors(replyToMessages = true) { (actor1, _, _) =>
      (actor1 ? 1).futureValue
      (actor1 ? 2).futureValue
      (actor1 ? Snapshot).futureValue
    }
    new EventSourcedCleanup(system).deleteAll("my-1", true).futureValue
    withTestActors(replyToMessages = true) { (actor1, _, _) =>
      (actor1 ? "state").futureValue.asInstanceOf[Int] shouldBe 0
    }
  }

}

class H2EventSourcedCleanupTest extends EventSourcedCleanupTest("h2-application.conf") with H2Cleaner
