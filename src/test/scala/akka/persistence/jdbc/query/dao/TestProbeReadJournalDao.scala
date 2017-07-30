package akka.persistence.jdbc.query.dao

import akka.NotUsed
import akka.persistence.jdbc.query.dao.TestProbeReadJournalDao.JournalSequence
import akka.persistence.{PersistentRepr, jdbc}
import akka.stream.scaladsl.Source
import akka.testkit.TestProbe
import akka.util.Timeout
import akka.pattern.ask

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

object TestProbeReadJournalDao {
  case class JournalSequence(offset: Long, limit: Long)
}

/**
 * Read journal dao where the journalSequence query is backed by a testprobe
 */
class TestProbeReadJournalDao(val probe: TestProbe) extends ReadJournalDao {
  // Since the testprobe is instrumented by the test, it should respond very fast
  implicit val askTimeout = Timeout(100.millis)

  /**
   * Returns distinct stream of persistenceIds
   */
  override def allPersistenceIdsSource(max: Long): Source[String, NotUsed] = ???

  /**
   * Returns a Source of bytes for certain tag from an offset. The result is sorted by
   * created time asc thus the offset is relative to the creation time
   */
  override def eventsByTag(tag: String, offset: Long, maxOffset: Long, max: Long): Source[Try[(PersistentRepr, Set[String], jdbc.JournalRow)], NotUsed] = ???

  /**
   * Returns a Source of bytes for a certain persistenceId
   */
  override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[Try[PersistentRepr], NotUsed] = ???

  /**
   * @param offset Minimum value to retrieve
   * @param limit  Maximum number of values to retrieve
   * @return A Source of journal event sequence numbers (corresponding to the Ordering column)
   */
  override def journalSequence(offset: Long, limit: Long): Source[Long, NotUsed] = {
    val f = probe.ref.ask(JournalSequence(offset, limit)).mapTo[scala.collection.immutable.Seq[Long]]
    Source.fromFuture(f).mapConcat(identity)
  }

  /**
   * @return The value of the maximum (ordering) id in the journal
   */
  override def maxJournalSequence(): Future[Long] = Future.successful(0)
}
