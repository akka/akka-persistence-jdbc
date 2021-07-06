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
import akka.persistence.jdbc.state.scaladsl.DurableStateSequenceActor.VisitedElement
import akka.persistence.jdbc.state.scaladsl.DurableStateSequenceActor.{ GetMaxGlobalOffset, MaxGlobalOffset }
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
        upsertForManyDifferentPersistenceIds(store, "pid", 1, "t1", 1, 400).size shouldBe 400

        withDurableStateSequenceActor(store, maxTries = 100) { actor =>
          eventually {
            actor.ask(GetMaxGlobalOffset).mapTo[MaxGlobalOffset].futureValue shouldBe MaxGlobalOffset(400)
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
  def fetchMaxGlobalOffset(durableStateSequenceActor: ActorRef): Future[Long] = {
    durableStateSequenceActor.ask(GetMaxGlobalOffset)(3.seconds).mapTo[MaxGlobalOffset].map(_.maxOffset)
  }

  it should "re-query with delay only when events are missing" in {
    val batchSize = 100
    val maxTries = 5
    val queryDelay = 300.millis

    val almostQueryDelay = queryDelay - 50.millis
    val almostImmediately = 50.millis
    withTestProbeDurableStateSequenceActor(batchSize, maxTries, queryDelay) { (daoProbe, _) =>
      daoProbe.expectMsg(almostImmediately, TestProbeDurableStateStoreQuery.StateInfoSequence(0, batchSize))
      val firstBatch = ((1L to 40L) ++ (51L to 110L)).map(n => VisitedElement(s"pid-$n", n, 1))
      daoProbe.reply(firstBatch)
      withClue(s"when events are missing, the actor should wait for $queryDelay before querying again") {
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(almostQueryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(40, batchSize))
      }
      // number 41 is still missing after this batch
      val secondBatch = (42L to 110L).map(n => VisitedElement(s"pid-$n", n, 1))
      daoProbe.reply(secondBatch)
      withClue(s"when events are missing, the actor should wait for $queryDelay before querying again") {
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(almostQueryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(40, batchSize))
      }
      val thirdBatch = (41L to 110L).map(n => VisitedElement(s"pid-$n", n, 1))
      daoProbe.reply(thirdBatch)
      withClue(
        s"when no more events are missing, but less that batchSize elemens have been received, " +
        s"the actor should wait for $queryDelay before querying again") {
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(almostQueryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(110, batchSize))
      }

      val fourthBatch = (111L to 210L).map(n => VisitedElement(s"pid-$n", n, 1))
      daoProbe.reply(fourthBatch)
      withClue(
        "When no more events are missing and the number of events received is equal to batchSize, " +
        "the actor should query again immediately") {
        daoProbe.expectMsg(almostImmediately, TestProbeDurableStateStoreQuery.StateInfoSequence(210, batchSize))
      }

      // Reply to prevent a dead letter warning on the timeout
      daoProbe.reply(Seq.empty)
      daoProbe.expectNoMessage(almostImmediately)
    }
  }

  it should "Assume an element missing after the configured amount of maxTries" in {
    val batchSize = 100
    val maxTries = 5
    val queryDelay = 300.millis

    val slightlyMoreThanQueryDelay = queryDelay + 100.millis
    val almostImmediately = 50.millis

    val allIds = ((1L to 40L) ++ (43L to 200L)).map(n => VisitedElement(s"pid-$n", n, 1))

    withTestProbeDurableStateSequenceActor(batchSize, maxTries, queryDelay) { (daoProbe, actor) =>
      daoProbe.expectMsg(almostImmediately, TestProbeDurableStateStoreQuery.StateInfoSequence(0, batchSize))
      daoProbe.reply(allIds.take(100))

      val idsLargerThan40 = allIds.dropWhile(_.offset <= 40)
      val retryResponse = idsLargerThan40.take(100)
      for (i <- 1 to maxTries) withClue(s"should retry $maxTries times (attempt $i)") {
        daoProbe.expectMsg(slightlyMoreThanQueryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(40, batchSize))
        daoProbe.reply(retryResponse)
      }

      // sanity check
      retryResponse.last.offset shouldBe 142
      withClue(
        "The elements 41 and 42 should be assumed missing, " +
        "the actor should query again immediately since a full batch has been received") {
        daoProbe.expectMsg(almostImmediately, TestProbeDurableStateStoreQuery.StateInfoSequence(142, batchSize))
        fetchMaxGlobalOffset(actor).futureValue shouldBe 142
      }

      // Reply to prevent a dead letter warning on the timeout
      daoProbe.reply(Seq.empty)
      daoProbe.expectNoMessage(almostImmediately)
    }
  }

  it should "not delay several updates to known pid" in {
    val batchSize = 7
    val maxTries = 5
    val queryDelay = 300.millis
    import DurableStateSequenceActor.VisitedElement

    val almostQueryDelay = queryDelay - 50.millis
    val almostImmediately = 50.millis

    withTestProbeDurableStateSequenceActor(batchSize, maxTries, queryDelay) { (daoProbe, actor) =>
      daoProbe.expectMsg(almostImmediately, TestProbeDurableStateStoreQuery.StateInfoSequence(0, batchSize))

      val firstBatch = List(VisitedElement("p1", 1, 1), VisitedElement("p2", 2, 1), VisitedElement("p3", 3, 1))

      daoProbe.reply(firstBatch)
      withClue(s"when offsets are not missing ") {
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(queryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(3, batchSize))
        fetchMaxGlobalOffset(actor).futureValue shouldBe 3
      }

      // two updates to p3
      val secondBatch = List(VisitedElement("p1", 4, 2), VisitedElement("p2", 5, 2), VisitedElement("p3", 7, 3))

      daoProbe.reply(secondBatch)
      withClue(s"when several updates to known pid ") {
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(queryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(7, batchSize))
        fetchMaxGlobalOffset(actor).futureValue shouldBe 7
      }

      // five updates to p2 and three to p3
      val thirdBatch = List(VisitedElement("p1", 8, 3), VisitedElement("p2", 13, 7), VisitedElement("p3", 16, 6))

      daoProbe.reply(thirdBatch)
      withClue(s"when several updates to known pid ") {
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(queryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(16, batchSize))
        fetchMaxGlobalOffset(actor).futureValue shouldBe 16
      }

      // Reply to prevent a dead letter warning on the timeout
      daoProbe.reply(Seq.empty)
      daoProbe.expectNoMessage(almostImmediately)
    }
  }

  it should "not delay more complex updates from several pids" in {
    val batchSize = 7
    val maxTries = 5
    val queryDelay = 300.millis
    import DurableStateSequenceActor.VisitedElement

    val almostQueryDelay = queryDelay - 50.millis
    val almostImmediately = 50.millis
    val slightlyMoreThanQueryDelay = queryDelay + 100.millis

    withTestProbeDurableStateSequenceActor(batchSize, maxTries, queryDelay) { (daoProbe, actor) =>
      daoProbe.expectMsg(almostImmediately, TestProbeDurableStateStoreQuery.StateInfoSequence(0, batchSize))

      val firstBatch = List(VisitedElement("p1", 1, 1), VisitedElement("p2", 2, 1), VisitedElement("p3", 3, 1))

      daoProbe.reply(firstBatch)
      withClue(s"when offsets are not missing ") {
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(queryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(3, batchSize))
        fetchMaxGlobalOffset(actor).futureValue shouldBe 3
      }

      // updates like this:
      // p1, 4, 2 <<<
      // p2, 5, 2
      // p2, 6, 3
      // p2, 7, 4
      // p3, 8, 2
      // p3, 9, 3
      // p3, 10, 4 <<<
      // p2, 11, 5 <<<
      val secondBatch = List(VisitedElement("p1", 4, 2), VisitedElement("p3", 10, 4), VisitedElement("p2", 11, 5))

      daoProbe.reply(secondBatch)
      daoProbe.expectMsg(slightlyMoreThanQueryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(11, batchSize))
      fetchMaxGlobalOffset(actor).futureValue shouldBe 11

      // Reply to prevent a dead letter warning on the timeout
      daoProbe.reply(Seq.empty)
      daoProbe.expectNoMessage(almostImmediately)
    }
  }

  it should "re-query for unknown pid" in {
    val batchSize = 7
    val maxTries = 5
    val queryDelay = 300.millis
    import DurableStateSequenceActor.VisitedElement

    val almostQueryDelay = queryDelay - 50.millis
    val almostImmediately = 50.millis
    val slightlyMoreThanQueryDelay = queryDelay + 100.millis

    withTestProbeDurableStateSequenceActor(batchSize, maxTries, queryDelay) { (daoProbe, actor) =>
      daoProbe.expectMsg(almostImmediately, TestProbeDurableStateStoreQuery.StateInfoSequence(0, batchSize))

      val firstBatch = List(VisitedElement("p1", 1, 1))

      daoProbe.reply(firstBatch)
      withClue(s"when offsets are not missing ") {
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(queryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(1, batchSize))
        fetchMaxGlobalOffset(actor).futureValue shouldBe 1
      }

      // updates like this:
      // p1, 2, 2 <<<
      // p2, 3, 2
      // p2, 4, 3 <<<
      // p3, 5, 2
      // p3, 6, 3
      // p3, 7, 4 <<<
      val secondBatch = List(VisitedElement("p1", 2, 2), VisitedElement("p2", 4, 3), VisitedElement("p3", 7, 4))

      daoProbe.reply(secondBatch)
      for (i <- 1 to maxTries) withClue(s"when updates to unknown pid, attempt $i ") {
        val expectedQueryOffset = 2 // p1 offset 2 is ok
        daoProbe.expectMsg(
          slightlyMoreThanQueryDelay,
          TestProbeDurableStateStoreQuery.StateInfoSequence(expectedQueryOffset, batchSize))
        daoProbe.reply(secondBatch)
      }

      daoProbe.expectMsg(slightlyMoreThanQueryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(7, batchSize))
      fetchMaxGlobalOffset(actor).futureValue shouldBe 7

      // two updates for p2 and p3 but now they are known and gaps can be filled
      val thirdBatch = List(VisitedElement("p2", 9, 5), VisitedElement("p3", 11, 6))
      daoProbe.reply(thirdBatch)
      daoProbe.expectMsg(slightlyMoreThanQueryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(11, batchSize))
      fetchMaxGlobalOffset(actor).futureValue shouldBe 11

      // Reply to prevent a dead letter warning on the timeout
      daoProbe.reply(Seq.empty)
      daoProbe.expectNoMessage(almostImmediately)
    }
  }

  it should "evict revision cache when exceeding capacity" in {
    val batchSize = 100
    val maxTries = 5
    val queryDelay = 300.millis
    import DurableStateSequenceActor.VisitedElement

    val almostImmediately = 50.millis
    val slightlyMoreThanQueryDelay = queryDelay + 100.millis

    withTestProbeDurableStateSequenceActor(batchSize, maxTries, queryDelay, revisionCacheCapacity = 5) {
      (daoProbe, actor) =>
        daoProbe.expectMsg(almostImmediately, TestProbeDurableStateStoreQuery.StateInfoSequence(0, batchSize))

        val firstBatch = List(
          VisitedElement("p1", 1, 1),
          VisitedElement("p2", 2, 1),
          VisitedElement("p3", 3, 1),
          VisitedElement("p4", 4, 1),
          VisitedElement("p5", 5, 1))
        daoProbe.reply(firstBatch)
        withClue(s"when offsets are not missing ") {
          daoProbe.expectMsg(
            slightlyMoreThanQueryDelay,
            TestProbeDurableStateStoreQuery.StateInfoSequence(5, batchSize))
          fetchMaxGlobalOffset(actor).futureValue shouldBe 5
        }

        // pids in cache
        val secondBatch = List(
          VisitedElement("p1", 7, 3),
          VisitedElement("p2", 9, 3),
          VisitedElement("p3", 11, 3),
          VisitedElement("p4", 13, 3))
        daoProbe.reply(secondBatch)
        withClue(s"when offsets are not missing ") {
          daoProbe.expectMsg(
            slightlyMoreThanQueryDelay,
            TestProbeDurableStateStoreQuery.StateInfoSequence(13, batchSize))
          fetchMaxGlobalOffset(actor).futureValue shouldBe 13
        }

        // exceeding cache capacity of 5, p1, p2, p5 will be evicted because lowest offset
        val thirdBatch = List(VisitedElement("p4", 15, 5), VisitedElement("p6", 16, 1), VisitedElement("p7", 17, 1))
        daoProbe.reply(thirdBatch)
        withClue(s"when offsets are not missing ") {
          daoProbe.expectMsg(
            slightlyMoreThanQueryDelay,
            TestProbeDurableStateStoreQuery.StateInfoSequence(17, batchSize))
          fetchMaxGlobalOffset(actor).futureValue shouldBe 17
        }

        // p1, p2, p5 were evicted because lowest offset
        val fourthBatch = List(VisitedElement("p2", 19, 5), VisitedElement("p1", 21, 5), VisitedElement("p5", 23, 3))
        daoProbe.reply(fourthBatch)
        for (i <- 1 to maxTries) withClue(s"when updates to unknown pid, attempt $i ") {
          daoProbe
            .expectMsg(slightlyMoreThanQueryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(17, batchSize))
          daoProbe.reply(fourthBatch)
        }

        daoProbe.expectMsg(slightlyMoreThanQueryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(23, batchSize))
        fetchMaxGlobalOffset(actor).futureValue shouldBe 23

        // Reply to prevent a dead letter warning on the timeout
        daoProbe.reply(Seq.empty)
        daoProbe.expectNoMessage(almostImmediately)
    }
  }

  import akka.persistence.jdbc.config.DurableStateTableConfiguration
  def withTestProbeDurableStateSequenceActor(
      batchSize: Int,
      maxTries: Int,
      queryDelay: FiniteDuration,
      revisionCacheCapacity: Int = 10000)(f: (TestProbe, ActorRef) => Unit)(implicit system: ActorSystem): Unit = {
    val testProbe = TestProbe()

    val customConfig = ConfigFactory.parseString(s"""
      jdbc-durable-state-store {
        batchSize = $batchSize
        refreshInterval = 500.milliseconds
        durable-state-sequence-retrieval {
          query-delay = $queryDelay
          max-tries = $maxTries
          max-backoff-query-delay = 4.seconds
          ask-timeout = 100.millis
          batch-size = $batchSize
          revision-cache-capacity = $revisionCacheCapacity
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
