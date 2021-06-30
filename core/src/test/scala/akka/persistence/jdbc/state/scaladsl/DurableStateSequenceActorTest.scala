/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.state.scaladsl

import scala.concurrent.Future
import scala.concurrent.duration._
import com.typesafe.config.{ Config, ConfigFactory }
import akka.actor.{ ActorRef, ActorSystem, ExtendedActorSystem }
import akka.pattern.ask
import akka.persistence.jdbc.SharedActorSystemTestSpec
import akka.persistence.jdbc.state.scaladsl.DurableStateSequenceActor.{ GetMaxOrderingId, MaxOrderingId }
import akka.persistence.jdbc.testkit.internal.{ H2, SchemaType }
import akka.testkit.TestProbe
import akka.util.Timeout
import org.scalatest.concurrent.Eventually

abstract class DurableStateSequenceActorTest(config: Config, schemaType: SchemaType)
    extends StateSpecBase(config, schemaType)
    with DataGenerationHelper
    with Eventually {

  val durableStateSequenceActorConfig = durableStateConfig.stateSequenceConfig

  implicit val askTimeout = 50.millis
  implicit val timeout: Timeout = Timeout(1.minute)

  "A DurableStateSequenceActor" must {
    "recover normally" in {
      withActorSystem { implicit system =>
        val store =
          new JdbcDurableStateStore[String](db, schemaTypeToProfile(schemaType), durableStateConfig, serialization)
        upsertManyFor(store, "p1", "t1", 1, 400).size shouldBe 400

        withDurableStateSequenceActor(store, maxTries = 100) { actor =>
          eventually {
            actor.ask(GetMaxOrderingId).mapTo[MaxOrderingId].futureValue shouldBe MaxOrderingId(400)
          }
        }
      }
    }
  }

  /**
   * @param maxTries The number of tries before events are assumed missing
   *                 (since the actor queries every second by default,
   *                 this is effectively the number of seconds after which events are assumed missing)
   */
  def withDurableStateSequenceActor(store: JdbcDurableStateStore[String], maxTries: Int)(f: ActorRef => Unit)(
      implicit system: ActorSystem): Unit = {
    val actor =
      system.actorOf(DurableStateSequenceActor.props(store, durableStateSequenceActorConfig.copy(maxTries = maxTries)))
    try f(actor)
    finally system.stop(actor)
  }
}

class MockDurableStateSequenceActorTest extends SharedActorSystemTestSpec {
  def fetchMaxOrderingId(durableStateSequenceActor: ActorRef): Future[Long] = {
    durableStateSequenceActor.ask(GetMaxOrderingId)(20.millis).mapTo[MaxOrderingId].map(_.maxOrdering)
  }

  it should "re-query with delay only when events are missing." in {
    val batchSize = 100
    val maxTries = 5
    val queryDelay = 200.millis

    val almostQueryDelay = queryDelay - 50.millis
    val almostImmediately = 50.millis
    withTestProbeDurableStateSequenceActor(batchSize, maxTries, queryDelay) { (daoProbe, _) =>
      daoProbe.expectMsg(almostImmediately, TestProbeDurableStateStoreQuery.OffsetSequence(0, batchSize))
      val firstBatch = (1L to 40L) ++ (51L to 110L)
      daoProbe.reply(firstBatch)
      withClue(s"when events are missing, the actor should wait for $queryDelay before querying again") {
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(almostQueryDelay, TestProbeDurableStateStoreQuery.OffsetSequence(40, batchSize))
      }
      // number 41 is still missing after this batch
      val secondBatch = 42L to 110L
      daoProbe.reply(secondBatch)
      withClue(s"when events are missing, the actor should wait for $queryDelay before querying again") {
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(almostQueryDelay, TestProbeDurableStateStoreQuery.OffsetSequence(40, batchSize))
      }
      val thirdBatch = 41L to 110L
      daoProbe.reply(thirdBatch)
      withClue(
        s"when no more events are missing, but less that batchSize elemens have been received, " +
        s"the actor should wait for $queryDelay before querying again") {
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(almostQueryDelay, TestProbeDurableStateStoreQuery.OffsetSequence(110, batchSize))
      }

      val fourthBatch = 111L to 210L
      daoProbe.reply(fourthBatch)
      withClue(
        "When no more events are missing and the number of events received is equal to batchSize, " +
        "the actor should query again immediately") {
        daoProbe.expectMsg(almostImmediately, TestProbeDurableStateStoreQuery.OffsetSequence(210, batchSize))
      }

      // Reply to prevent a dead letter warning on the timeout
      daoProbe.reply(Seq.empty)
      daoProbe.expectNoMessage(almostImmediately)
    }
  }

  it should "Assume an element missing after the configured amount of maxTries" in {
    val batchSize = 100
    val maxTries = 5
    val queryDelay = 150.millis

    val slightlyMoreThanQueryDelay = queryDelay + 50.millis
    val almostImmediately = 20.millis

    val allIds = (1L to 40L) ++ (43L to 200L)

    withTestProbeDurableStateSequenceActor(batchSize, maxTries, queryDelay) { (daoProbe, actor) =>
      daoProbe.expectMsg(almostImmediately, TestProbeDurableStateStoreQuery.OffsetSequence(0, batchSize))
      daoProbe.reply(allIds.take(100))

      val idsLargerThan40 = allIds.dropWhile(_ <= 40)
      val retryResponse = idsLargerThan40.take(100)
      for (i <- 1 to maxTries) withClue(s"should retry $maxTries times (attempt $i)") {
        daoProbe.expectMsg(slightlyMoreThanQueryDelay, TestProbeDurableStateStoreQuery.OffsetSequence(40, batchSize))
        daoProbe.reply(retryResponse)
      }

      // sanity check
      retryResponse.last shouldBe 142
      withClue(
        "The elements 41 and 42 should be assumed missing, " +
        "the actor should query again immediately since a full batch has been received") {
        daoProbe.expectMsg(almostImmediately, TestProbeDurableStateStoreQuery.OffsetSequence(142, batchSize))
        fetchMaxOrderingId(actor).futureValue shouldBe 142
      }

      // Reply to prevent a dead letter warning on the timeout
      daoProbe.reply(Seq.empty)
      daoProbe.expectNoMessage(almostImmediately)
    }
  }

  import akka.persistence.jdbc.config.DurableStateTableConfiguration
  def withTestProbeDurableStateSequenceActor(batchSize: Int, maxTries: Int, queryDelay: FiniteDuration)(
      f: (TestProbe, ActorRef) => Unit)(implicit system: ActorSystem): Unit = {
    val testProbe = TestProbe()

    val customConfig = ConfigFactory.parseString(s"""
      jdbc-durable-state-store {
        schemaType = H2
        batchSize = ${batchSize}
        refreshInterval = 500.milliseconds
        durable-state-sequence-retrieval {
          query-delay = ${queryDelay}
          max-tries = ${maxTries}
          max-backoff-query-delay = 4.seconds
          ask-timeout = 100.millis
          batch-size = ${batchSize}
        }
      }
    """)

    lazy val cfg = customConfig
      .getConfig("jdbc-durable-state-store")
      .withFallback(system.settings.config.getConfig("jdbc-durable-state-store"))
      .withFallback(ConfigFactory.load("h2-application.conf").getConfig("jdbc-durable-state-store"))

    val stateTableConfig = new DurableStateTableConfiguration(cfg)

    val mockStore =
      new TestProbeDurableStateStoreQuery(testProbe, db, slick.jdbc.H2Profile, stateTableConfig, serialization)(
        system.asInstanceOf[ExtendedActorSystem])
    val actor = system.actorOf(
      DurableStateSequenceActor
        .props(mockStore.asInstanceOf[JdbcDurableStateStore[String]], stateTableConfig.stateSequenceConfig))
    try f(testProbe, actor)
    finally system.stop(actor)
  }
}

class H2DurableStateSequenceActorTest
    extends DurableStateSequenceActorTest(ConfigFactory.load("h2-application.conf"), H2) {
  implicit lazy val system: ActorSystem =
    ActorSystem("test", config.withFallback(customSerializers))
}
