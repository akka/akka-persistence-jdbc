/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.query

import akka.actor.{ ActorRef, ActorSystem }
import akka.pattern.ask
import akka.persistence.jdbc.config.JournalSequenceRetrievalConfig
import akka.persistence.jdbc.journal.dao.legacy.{ JournalRow, JournalTables }
import akka.persistence.jdbc.query.JournalSequenceActor.{ GetMaxOrderingId, MaxOrderingId }
import akka.persistence.jdbc.query.dao.TestProbeReadJournalDao
import akka.persistence.jdbc.SharedActorSystemTestSpec
import akka.persistence.jdbc.query.dao.legacy.ByteArrayReadJournalDao
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.{ Sink, Source }
import akka.testkit.TestProbe
import org.slf4j.LoggerFactory
import slick.jdbc.{ JdbcBackend, JdbcCapabilities }

import scala.concurrent.Future
import scala.concurrent.duration._
import org.scalatest.time.Span

abstract class JournalSequenceActorTest(configFile: String, isOracle: Boolean)
    extends QueryTestSpec(configFile)
    with JournalTables {
  private val log = LoggerFactory.getLogger(classOf[JournalSequenceActorTest])

  val journalSequenceActorConfig = readJournalConfig.journalSequenceRetrievalConfiguration
  val journalTableCfg = journalConfig.journalTableConfiguration

  import profile.api._

  implicit val askTimeout: FiniteDuration = 50.millis

  def generateId: Int = 0

  behavior.of("JournalSequenceActor")

  it should "recover normally" in {
    if (newDao)
      pending
    withActorSystem { implicit system: ActorSystem =>
      withDatabase { db =>
        val numberOfRows = 15000
        val rows = for (i <- 1 to numberOfRows) yield JournalRow(generateId, deleted = false, "id", i, Array(0.toByte))
        db.run(JournalTable ++= rows).futureValue
        withJournalSequenceActor(db, maxTries = 100) { actor =>
          eventually {
            actor.ask(GetMaxOrderingId).mapTo[MaxOrderingId].futureValue shouldBe MaxOrderingId(numberOfRows)
          }
        }
      }
    }
  }

  private def canForceInsert: Boolean = profile.capabilities.contains(JdbcCapabilities.forceInsert)

  if (canForceInsert && !newDao) {
    it should s"recover ${if (isOracle) "one hundred thousand" else "one million"} events quickly if no ids are missing" in {
      withActorSystem { implicit system: ActorSystem =>
        withDatabase { db =>
          val elements = if (isOracle) 100000 else 1000000
          Source
            .fromIterator(() => (1 to elements).iterator)
            .map(id => JournalRow(id, deleted = false, "id", id, Array(0.toByte)))
            .grouped(10000)
            .mapAsync(4) { rows =>
              db.run(JournalTable.forceInsertAll(rows))
            }
            .runWith(Sink.ignore)
            .futureValue

          val startTime = System.currentTimeMillis()
          withJournalSequenceActor(db, maxTries = 100) { actor =>
            implicit val patienceConfig: PatienceConfig =
              PatienceConfig(10.seconds, Span(200, org.scalatest.time.Millis))
            eventually {
              val currentMax = actor.ask(GetMaxOrderingId).mapTo[MaxOrderingId].futureValue.maxOrdering
              currentMax shouldBe elements
            }
          }
          val timeTaken = System.currentTimeMillis() - startTime
          log.info(s"Recovered all events in $timeTaken ms")
        }
      }
    }
  }

  if (!isOracle && canForceInsert && !newDao) {
    // Note this test case cannot be executed for oracle, because forceInsertAll is not supported in the oracle driver.
    it should "recover after the specified max number if tries if the first event has a very high sequence number and lots of large gaps exist" in {
      withActorSystem { implicit system: ActorSystem =>
        withDatabase { db =>
          val numElements = 1000
          val gapSize = 10000
          val firstElement = 100000000
          val lastElement = firstElement + (numElements * gapSize)
          Source
            .fromIterator(() => (firstElement to lastElement by gapSize).iterator)
            .map(id => JournalRow(id, deleted = false, "id", id, Array(0.toByte)))
            .grouped(10000)
            .mapAsync(4) { rows =>
              db.run(JournalTable.forceInsertAll(rows))
            }
            .runWith(Sink.ignore)
            .futureValue

          withJournalSequenceActor(db, maxTries = 2) { actor =>
            // Should normally recover after `maxTries` seconds
            implicit val patienceConfig: PatienceConfig =
              PatienceConfig(10.seconds, Span(200, org.scalatest.time.Millis))
            eventually {
              val currentMax = actor.ask(GetMaxOrderingId).mapTo[MaxOrderingId].futureValue.maxOrdering
              currentMax shouldBe lastElement
            }
          }
        }
      }
    }
  }

  if (canForceInsert && !newDao) {
    it should s"assume that the max ordering id in the database on startup is the max after (queryDelay * maxTries)" in {
      withActorSystem { implicit system: ActorSystem =>
        withDatabase { db =>
          val maxElement = 100000
          // only even numbers, odd numbers are missing
          val idSeq = 2 to maxElement by 2
          Source
            .fromIterator(() => idSeq.iterator)
            .map(id => JournalRow(id, deleted = false, "id", id, Array(0.toByte)))
            .grouped(10000)
            .mapAsync(4) { rows =>
              db.run(JournalTable.forceInsertAll(rows))
            }
            .runWith(Sink.ignore)
            .futureValue

          val highestValue = if (isOracle) {
            // ForceInsert does not seem to work for oracle, we must delete the odd numbered events
            db.run(JournalTable.filter(_.ordering % 2L === 1L).delete).futureValue
            maxElement / 2
          } else maxElement

          withJournalSequenceActor(db, maxTries = 2) { actor =>
            // The actor should assume the max after 2 seconds
            implicit val patienceConfig: PatienceConfig = PatienceConfig(3.seconds)
            eventually {
              val currentMax = actor.ask(GetMaxOrderingId).mapTo[MaxOrderingId].futureValue.maxOrdering
              currentMax shouldBe highestValue
            }
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
  def withJournalSequenceActor(db: JdbcBackend.Database, maxTries: Int)(f: ActorRef => Unit)(
      implicit system: ActorSystem): Unit = {
    import system.dispatcher
    val readJournalDao = new ByteArrayReadJournalDao(db, profile, readJournalConfig, SerializationExtension(system))
    val actor =
      system.actorOf(JournalSequenceActor.props(readJournalDao, journalSequenceActorConfig.copy(maxTries = maxTries)))
    try f(actor)
    finally system.stop(actor)
  }
}

class MockDaoJournalSequenceActorTest extends SharedActorSystemTestSpec {
  def fetchMaxOrderingId(journalSequenceActor: ActorRef): Future[Long] = {
    journalSequenceActor.ask(GetMaxOrderingId)(20.millis).mapTo[MaxOrderingId].map(_.maxOrdering)
  }

  it should "re-query with delay only when events are missing." in {
    val batchSize = 100
    val maxTries = 5
    val queryDelay = 200.millis

    val almostQueryDelay = queryDelay - 50.millis
    val almostImmediately = 50.millis
    withTestProbeJournalSequenceActor(batchSize, maxTries, queryDelay) { (daoProbe, _) =>
      daoProbe.expectMsg(almostImmediately, TestProbeReadJournalDao.JournalSequence(0, batchSize))
      val firstBatch = (1L to 40L) ++ (51L to 110L)
      daoProbe.reply(firstBatch)
      withClue(s"when events are missing, the actor should wait for $queryDelay before querying again") {
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(almostQueryDelay, TestProbeReadJournalDao.JournalSequence(40, batchSize))
      }
      // number 41 is still missing after this batch
      val secondBatch = 42L to 110L
      daoProbe.reply(secondBatch)
      withClue(s"when events are missing, the actor should wait for $queryDelay before querying again") {
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(almostQueryDelay, TestProbeReadJournalDao.JournalSequence(40, batchSize))
      }
      val thirdBatch = 41L to 110L
      daoProbe.reply(thirdBatch)
      withClue(
        s"when no more events are missing, but less that batchSize elemens have been received, " +
        s"the actor should wait for $queryDelay before querying again") {
        daoProbe.expectNoMessage(almostQueryDelay)
        daoProbe.expectMsg(almostQueryDelay, TestProbeReadJournalDao.JournalSequence(110, batchSize))
      }

      val fourthBatch = 111L to 210L
      daoProbe.reply(fourthBatch)
      withClue(
        "When no more events are missing and the number of events received is equal to batchSize, " +
        "the actor should query again immediately") {
        daoProbe.expectMsg(almostImmediately, TestProbeReadJournalDao.JournalSequence(210, batchSize))
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

    withTestProbeJournalSequenceActor(batchSize, maxTries, queryDelay) { (daoProbe, actor) =>
      daoProbe.expectMsg(almostImmediately, TestProbeReadJournalDao.JournalSequence(0, batchSize))
      daoProbe.reply(allIds.take(100))

      val idsLargerThan40 = allIds.dropWhile(_ <= 40)
      val retryResponse = idsLargerThan40.take(100)
      for (i <- 1 to maxTries) withClue(s"should retry $maxTries times (attempt $i)") {
        daoProbe.expectMsg(slightlyMoreThanQueryDelay, TestProbeReadJournalDao.JournalSequence(40, batchSize))
        daoProbe.reply(retryResponse)
      }

      // sanity check
      retryResponse.last shouldBe 142
      withClue(
        "The elements 41 and 42 should be assumed missing, " +
        "the actor should query again immediately since a full batch has been received") {
        daoProbe.expectMsg(almostImmediately, TestProbeReadJournalDao.JournalSequence(142, batchSize))
        fetchMaxOrderingId(actor).futureValue shouldBe 142
      }

      // Reply to prevent a dead letter warning on the timeout
      daoProbe.reply(Seq.empty)
      daoProbe.expectNoMessage(almostImmediately)
    }
  }

  def withTestProbeJournalSequenceActor(batchSize: Int, maxTries: Int, queryDelay: FiniteDuration)(
      f: (TestProbe, ActorRef) => Unit)(implicit system: ActorSystem): Unit = {
    val testProbe = TestProbe()
    val config = JournalSequenceRetrievalConfig(
      batchSize = batchSize,
      maxTries = maxTries,
      queryDelay = queryDelay,
      maxBackoffQueryDelay = 4.seconds,
      askTimeout = 100.millis)
    val mockDao = new TestProbeReadJournalDao(testProbe)
    val actor = system.actorOf(JournalSequenceActor.props(mockDao, config))
    try f(testProbe, actor)
    finally system.stop(actor)
  }
}

class H2JournalSequenceActorTest
    extends JournalSequenceActorTest("h2-application.conf", isOracle = false)
    with H2Cleaner
