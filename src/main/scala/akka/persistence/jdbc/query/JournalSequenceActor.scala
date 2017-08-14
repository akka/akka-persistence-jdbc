package akka.persistence.jdbc.query

import akka.actor.{Actor, ActorLogging, Props, Status, Timers}
import akka.persistence.jdbc.query.dao.ReadJournalDao
import akka.pattern.pipe
import akka.persistence.jdbc.config.JournalSequenceRetrievalConfig
import akka.stream.Materializer
import akka.stream.scaladsl.Sink

import scala.concurrent.duration.FiniteDuration

object JournalSequenceActor {
  def props(readJournalDao: ReadJournalDao, config: JournalSequenceRetrievalConfig)(implicit materializer: Materializer): Props = Props(new JournalSequenceActor(readJournalDao, config))

  private case object QueryOrderingIds
  private case class NewOrderingIds(elements: Seq[OrderingId])

  private case class ScheduleAssumeMaxOrderingId(max: OrderingId)
  private case class AssumeMaxOrderingId(max: OrderingId)

  case object GetMaxOrderingId
  case class MaxOrderingId(maxOrdering: OrderingId)

  private case object QueryOrderingIdsTimerKey
  private case object AssumeMaxOrderingIdTimerKey

  private type OrderingId = Long
}

/**
 * To support the EventsByTag query, this actor keeps track of which rows are visible in the database.
 * This is required to guarantee the EventByTag does not skip any rows in case rows with a higher (ordering) id are
 * visible in the database before rows with a lower (ordering) id.
 */
class JournalSequenceActor(readJournalDao: ReadJournalDao, config: JournalSequenceRetrievalConfig)(implicit materializer: Materializer) extends Actor with ActorLogging with Timers {
  import JournalSequenceActor._
  import context.dispatcher
  import config.{maxTries, maxBackoffQueryDelay, queryDelay, batchSize}

  override def receive: Receive = receive(0L, Map.empty, 0)

  override def preStart(): Unit = {
    self ! QueryOrderingIds
    readJournalDao.maxJournalSequence().mapTo[Long].onComplete {
      case scala.util.Success(maxInDatabase) =>
        self ! ScheduleAssumeMaxOrderingId(maxInDatabase)
      case scala.util.Failure(t) =>
        log.info("Failed to recover fast, using event-by-event recovery instead. Cause: {}", t)
    }
  }

  /**
   * @param currentMaxOrdering The highest ordering value for which it is known that no missing elements exist
   * @param missingByCounter A map with missing orderingIds. The key of the map is the count at which the missing elements
   *                         can be assumed to be "skipped ids" (they are no longer assumed missing).
   * @param moduloCounter A counter which is incremented every time a new query have been executed, modulo `maxTries`
   * @param previousDelay The last used delay (may change in case failures occur)
   */
  def receive(currentMaxOrdering: OrderingId, missingByCounter: Map[Int, Set[OrderingId]], moduloCounter: Int, previousDelay: FiniteDuration = queryDelay): Receive = {
    case ScheduleAssumeMaxOrderingId(max) =>
      // All elements smaller than max can be assumed missing after this delay
      val delay = queryDelay * maxTries
      timers.startSingleTimer(key = AssumeMaxOrderingIdTimerKey, AssumeMaxOrderingId(max), delay)
    case AssumeMaxOrderingId(max) =>
      if (currentMaxOrdering < max) {
        context.become(receive(max, missingByCounter, moduloCounter, previousDelay))
      }
    case GetMaxOrderingId =>
      sender() ! MaxOrderingId(currentMaxOrdering)
    case QueryOrderingIds =>
      readJournalDao.journalSequence(currentMaxOrdering, batchSize).runWith(Sink.seq)
        .map(result => NewOrderingIds(result)) pipeTo self
    case NewOrderingIds(elements) =>
      val givenUp = missingByCounter.getOrElse(moduloCounter, Set.empty)
      val (nextmax, _, missingElems) = elements.foldLeft[(OrderingId, OrderingId, Set[OrderingId])](currentMaxOrdering, currentMaxOrdering, Set.empty) {
        case ((currentMax, previousElement, missing), elem) =>
          val newMissing = if (previousElement + 1 < elem && !givenUp(elem)) {
            val currentlyMissing = previousElement + 1 until elem
            def alreadyMissing(e: Long) = missingByCounter.values.exists(_.contains(e))
            missing ++ currentlyMissing.filterNot(alreadyMissing)
          } else missing
          val newMax =
            if (currentMax + 1 == elem) elem
            else if ((currentMax + 1).until(elem).forall(givenUp.contains)) elem
            else currentMax
          (newMax, elem, newMissing)
      }
      val newMissingByCounter = (missingByCounter + (moduloCounter -> missingElems)).map {
        case (key, value) =>
          key -> value.filter(missingId => missingId > nextmax)
      }
      if (nextmax - currentMaxOrdering >= batchSize && newMissingByCounter.values.forall(_.isEmpty)) {
        // Many elements have been retrieved but none are missing
        // We can query again immediately, as this allows the actor to rapidly retrieve the real max ordering
        self ! QueryOrderingIds
        context.become(receive(nextmax, newMissingByCounter, moduloCounter))
      } else {
        scheduleQuery(queryDelay)
        context.become(receive(nextmax, newMissingByCounter, (moduloCounter + 1) % maxTries))
      }
    case Status.Failure(t) =>
      val newDelay = maxBackoffQueryDelay.min(previousDelay * 2)
      if (newDelay == maxBackoffQueryDelay) {
        log.warning("Failed to query max ordering id because of {}, retrying in {}", t, newDelay)
      }
      scheduleQuery(newDelay)
      context.become(receive(currentMaxOrdering, missingByCounter, moduloCounter, newDelay))
  }

  def scheduleQuery(delay: FiniteDuration): Unit = {
    timers.startSingleTimer(key = QueryOrderingIdsTimerKey, QueryOrderingIds, delay)
  }
}
