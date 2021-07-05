/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.state.scaladsl

import scala.concurrent.duration._
import com.typesafe.config.{ Config, ConfigFactory }
import akka.actor.{ ActorRef, ActorSystem, ExtendedActorSystem }
import akka.pattern.ask
import akka.persistence.jdbc.SharedActorSystemTestSpec
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
        upsertManyFor(store, "p1", "t1", 1, 400).size shouldBe 400

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
  it should "re-query with delay only when offsets are missing or when we fetch less than batch size" in {
    val batchSize = 5
    val maxTries = 5
    val queryDelay = 200.millis
    import DurableStateSequenceActor.VisitedElement

    val almostQueryDelay = queryDelay - 50.millis
    val almostImmediately = 50.millis

    withTestProbeDurableStateSequenceActor(batchSize, maxTries, queryDelay) { (daoProbe, _) =>
      daoProbe.expectMsg(almostImmediately, TestProbeDurableStateStoreQuery.StateInfoSequence(0, batchSize))

      // less than batch size
      // no offsets missing
      val firstBatch =
        List(VisitedElement("p2", 2978, 1000), VisitedElement("p3", 2995, 1000), VisitedElement("p1", 3000, 1000))

      daoProbe.reply(firstBatch)
      withClue(
        s"when offsets are not missing " +
        s"but the batch is not complete " +
        s"the actor should wait for $queryDelay before querying again") {
        // wait for delay
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(almostQueryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(3000, batchSize))
      }

      // introduce some missing offsets
      // a new pid with 1000 revisions but an offset increase of 3
      val secondBatch = List(VisitedElement("p4", 3003, 1000))

      daoProbe.reply(secondBatch)
      withClue(
        s"when offsets are missing " +
        s"the actor should wait for $queryDelay before querying again") {
        // wait for delay
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(almostQueryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(3003, batchSize))
      }

      // introduce another batch with no missing offsets
      // an already existing pid with 200 more revisions and an offset increase of 200
      val thirdBatch = List(VisitedElement("p1", 3204, 1200))

      daoProbe.reply(thirdBatch)
      withClue(
        s"when offsets are not missing for this batch " +
        s"but we have past missing offsets " +
        s"the actor should wait for $queryDelay before querying again") {
        // wait for delay
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(almostQueryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(3204, batchSize))
      }

      // Reply to prevent a dead letter warning on the timeout
      daoProbe.reply(Seq.empty)
      daoProbe.expectNoMessage(almostImmediately)
    }
  }

  it should "no re-query needed when offsets are not missing, batch is complete and we don't encounter new pids" in {
    val batchSize = 7
    val maxTries = 5
    val queryDelay = 200.millis
    import DurableStateSequenceActor.VisitedElement

    val almostQueryDelay = queryDelay - 50.millis
    val almostImmediately = 50.millis

    withTestProbeDurableStateSequenceActor(batchSize, maxTries, queryDelay) { (daoProbe, _) =>
      daoProbe.expectMsg(almostImmediately, TestProbeDurableStateStoreQuery.StateInfoSequence(0, batchSize))

      // matches batch size
      val firstBatch = List(
        VisitedElement("p6", 54, 10),
        VisitedElement("p2", 56, 10),
        VisitedElement("p1", 57, 10),
        VisitedElement("p7", 58, 10),
        VisitedElement("p3", 59, 10),
        VisitedElement("p4", 60, 10),
        VisitedElement("p5", 70, 10))

      daoProbe.reply(firstBatch)
      withClue(
        s"when offsets are not missing and " +
        s"the batch is complete " +
        s"but being the first batch, there will be cache miss " +
        s"hence the actor should delay and re-query") {
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(almostQueryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(70, batchSize))
      }

      val secondBatch = List(
        VisitedElement("p6", 71, 11),
        VisitedElement("p2", 72, 11),
        VisitedElement("p1", 73, 11),
        VisitedElement("p7", 74, 11),
        VisitedElement("p3", 75, 11),
        VisitedElement("p4", 76, 11),
        VisitedElement("p5", 77, 11))

      daoProbe.reply(secondBatch)
      withClue(
        s"when offsets are not missing and " +
        s"the batch is complete " +
        s"hence the actor should not delay") {
        daoProbe.expectMsg(almostQueryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(77, batchSize))
      }

      // introduce some missing offsets
      // a new pid with 10 revisions but an offset increase of 3
      val thirdBatch = List(VisitedElement("p8", 80, 10))

      daoProbe.reply(thirdBatch)
      withClue(
        s"when offsets are missing " +
        s"and we have a new pid (cache miss) " +
        s"the actor should wait for $queryDelay before querying again") {
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(almostQueryDelay, TestProbeDurableStateStoreQuery.StateInfoSequence(80, batchSize))
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
