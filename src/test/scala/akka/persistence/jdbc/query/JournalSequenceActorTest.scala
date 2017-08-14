package akka.persistence.jdbc.query

import akka.actor.ActorRef
import akka.pattern.ask
import akka.persistence.jdbc.config.JournalSequenceRetrievalConfig
import akka.persistence.jdbc.{JournalRow, MaterializerSpec, SimpleSpec}
import akka.persistence.jdbc.journal.dao.JournalTables
import akka.persistence.jdbc.query.JournalSequenceActor.{GetMaxOrderingId, MaxOrderingId}
import akka.persistence.jdbc.query.dao.TestProbeReadJournalDao
import akka.persistence.jdbc.util.SlickDriver
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestProbe
import slick.jdbc.JdbcProfile

import scala.concurrent.Future
import scala.concurrent.duration._

abstract class JournalSequenceActorTest(configFile: String, isOracle: Boolean) extends QueryTestSpec(configFile) with ScalaJdbcReadJournalOperations with JournalTables {

  val journalSequenceActorConfig = readJournal.readJournalConfig.journalSequenceRetrievalConfiguration
  val profile: JdbcProfile = SlickDriver.forDriverName(config)
  val journalTableCfg = journalConfig.journalTableConfiguration

  import profile.api._

  implicit val askTimeout = 50.millis

  def generateId: Int = 0

  behavior of "JournalSequenceActor"

  it should "recover normally" in {
    val numberOfRows = 15000
    val rows = for (i <- 1 to numberOfRows) yield JournalRow(generateId, deleted = false, "id", i, Array(0.toByte))
    db.run(JournalTable ++= rows).futureValue
    withJournalSequenceActor(maxTries = 100) { actor =>
      eventually {
        actor.ask(GetMaxOrderingId).mapTo[MaxOrderingId].futureValue shouldBe MaxOrderingId(numberOfRows)
      }
    }
  }

  it should s"recover ${if (isOracle) "one hundred thousand" else "one million"} events quickly if no ids are missing" in {
    val elements = if (isOracle) 100000 else 1000000
    Source.fromIterator(() => (1 to elements).iterator)
      .map(id => JournalRow(id, deleted = false, "id", id, Array(0.toByte)))
      .grouped(10000)
      .mapAsync(4) { rows =>
        db.run(JournalTable.forceInsertAll(rows))
      }
      .runWith(Sink.ignore).futureValue

    val startTime = System.currentTimeMillis()
    withJournalSequenceActor(maxTries = 100) { actor =>
      val patienceConfig = PatienceConfig(10.seconds)
      eventually {
        val currentMax = actor.ask(GetMaxOrderingId).mapTo[MaxOrderingId].futureValue.maxOrdering
        currentMax shouldBe elements
      }(patienceConfig, implicitly)
    }
    val timeTaken = System.currentTimeMillis() - startTime
    log.info(s"Recovered all events in $timeTaken ms")
  }

  it should s"assume that the max ordering id in the database on startup is the max after (queryDelay * maxTries)" in {
    val maxElement = 100000
    // only even numbers, odd numbers are missing
    val idSeq = 2 to maxElement by 2
    Source.fromIterator(() => idSeq.iterator)
      .map(id => JournalRow(id, deleted = false, "id", id, Array(0.toByte)))
      .grouped(10000)
      .mapAsync(4) { rows =>
        db.run(JournalTable.forceInsertAll(rows))
      }
      .runWith(Sink.ignore).futureValue

    val highestValue = if (isOracle) {
      // ForceInsert does not seem to work for oracle, we must delete the odd numbered events
      db.run(JournalTable.filter(_.ordering % 2L === 1L).delete).futureValue
      maxElement / 2
    } else maxElement

    withJournalSequenceActor(maxTries = 2) { actor =>
      // The actor should assume the max after 2 seconds
      val patienceConfig = PatienceConfig(3.seconds)
      eventually {
        val currentMax = actor.ask(GetMaxOrderingId).mapTo[MaxOrderingId].futureValue.maxOrdering
        currentMax shouldBe highestValue
      }(patienceConfig, implicitly)
    }
  }

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }

  /**
   * @param maxTries The number of tries before events are assumed missing
   *                 (since the actor queries every second by default,
   *                 this is effectively the number of seconds after which events are assumed missing)
   */
  def withJournalSequenceActor(maxTries: Int)(f: ActorRef => Unit): Unit = {
    val actor = system.actorOf(JournalSequenceActor.props(readJournal.readJournalDao, journalSequenceActorConfig.copy(maxTries = maxTries)))
    try f(actor) finally system.stop(actor)
  }

}

class MockDaoJournalSequenceActorTest extends SimpleSpec with MaterializerSpec {

  def fetchMaxOrderingId(journalSequenceActor: ActorRef): Future[Long] = {
    import system.dispatcher
    journalSequenceActor.ask(GetMaxOrderingId)(20.millis).mapTo[MaxOrderingId].map(_.maxOrdering)
  }

  it should "re-query with delay only when events are missing." in {
    val batchSize = 100
    val maxTries = 5
    val queryDelay = 200.millis

    val almostQueryDelay = queryDelay - 50.millis
    val almostImmediately = 50.millis
    withTestProbeJournalSequenceActor(batchSize, maxTries, queryDelay) { (daoProbe, actor) =>
      daoProbe.expectMsg(almostImmediately, TestProbeReadJournalDao.JournalSequence(0, batchSize))
      val firstBatch = (1L to 40L) ++ (51L to 110L)
      daoProbe.reply(firstBatch)
      withClue(s"when events are missing, the actor should wait for $queryDelay before querying again") {
        daoProbe.expectNoMsg(almostQueryDelay)
        daoProbe.expectMsg(almostQueryDelay, TestProbeReadJournalDao.JournalSequence(40, batchSize))
      }
      // number 41 is still missing after this batch
      val secondBatch = 42L to 110L
      daoProbe.reply(secondBatch)
      withClue(s"when events are missing, the actor should wait for $queryDelay before querying again") {
        daoProbe.expectNoMsg(almostQueryDelay)
        daoProbe.expectMsg(almostQueryDelay, TestProbeReadJournalDao.JournalSequence(40, batchSize))
      }
      val thirdBatch = 41L to 110L
      daoProbe.reply(thirdBatch)
      withClue(s"when no more events are missing, but less that batchSize elemens have been received, " +
        s"the actor should wait for $queryDelay before querying again") {
        daoProbe.expectNoMsg(almostQueryDelay)
        daoProbe.expectMsg(almostQueryDelay, TestProbeReadJournalDao.JournalSequence(110, batchSize))
      }

      val fourthBatch = 111L to 210L
      daoProbe.reply(fourthBatch)
      withClue("When no more events are missing and the number of events received is equal to batchSize, " +
        "the actor should query again immediately") {
        daoProbe.expectMsg(almostImmediately, TestProbeReadJournalDao.JournalSequence(210, batchSize))
      }

      // Reply to prevent a dead letter warning on the timeout
      daoProbe.reply(Seq.empty)
      daoProbe.expectNoMsg(almostImmediately)
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
      withClue("The elements 41 and 42 should be assumed missing") {
        fetchMaxOrderingId(actor).futureValue shouldBe 142
      }
      withClue("Since a full batch has been receive the actor should query again immediately") {
        daoProbe.expectMsg(almostImmediately, TestProbeReadJournalDao.JournalSequence(142, batchSize))
      }

      // Reply to prevent a dead letter warning on the timeout
      daoProbe.reply(Seq.empty)
      daoProbe.expectNoMsg(almostImmediately)
    }
  }

  def withTestProbeJournalSequenceActor(batchSize: Int, maxTries: Int, queryDelay: FiniteDuration)(f: (TestProbe, ActorRef) => Unit): Unit = {
    val testProbe = TestProbe()
    val config = JournalSequenceRetrievalConfig(batchSize = batchSize, maxTries = maxTries, queryDelay = queryDelay, maxBackoffQueryDelay = 4.seconds)
    val mockDao = new TestProbeReadJournalDao(testProbe)
    val actor = system.actorOf(JournalSequenceActor.props(mockDao, config))
    try f(testProbe, actor) finally system.stop(actor)
  }

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }
}

class PostgresJournalSequenceActorTest extends JournalSequenceActorTest("postgres-application.conf", isOracle = false) with PostgresCleaner

class MySQLJournalSequenceActorTest extends JournalSequenceActorTest("mysql-application.conf", isOracle = false) with MysqlCleaner

class OracleJournalSequenceActorTest extends JournalSequenceActorTest("oracle-application.conf", isOracle = true) with OracleCleaner

class H2JournalSequenceActorTest extends JournalSequenceActorTest("h2-application.conf", isOracle = false) with H2Cleaner