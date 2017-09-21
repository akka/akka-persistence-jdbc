package akka.persistence.jdbc.query

import akka.actor.{Actor, ActorLogging, Cancellable, Props, Status}
import akka.persistence.jdbc.query.dao.ReadJournalDao
import akka.pattern.pipe
import akka.persistence.jdbc.config.JournalSequenceRetrievalConfig
import akka.stream.Materializer
import akka.stream.scaladsl.Sink

import scala.concurrent.duration.FiniteDuration

object JournalSequenceActor {
  def props(readJournalDao: ReadJournalDao, config: JournalSequenceRetrievalConfig)(implicit materializer: Materializer): Props = Props(new JournalSequenceActor(readJournalDao, config))

  private case object QueryOrderingIds
  private case class NewOrderingIds(originalOffset: Long, elements: Seq[OrderingId])

  private case class AssumeMaxOrderingId(max: OrderingId)

  case object GetMaxOrderingId
  case class MaxOrderingId(maxOrdering: OrderingId)

  private type OrderingId = Long
}

/**
 * To support the EventsByTag query, this actor keeps track of which rows are visible in the database.
 * This is required to guarantee the EventByTag does not skip any rows in case rows with a higher (ordering) id are
 * visible in the database before rows with a lower (ordering) id.
 */
class JournalSequenceActor(readJournalDao: ReadJournalDao, config: JournalSequenceRetrievalConfig)(implicit materializer: Materializer) extends Actor with ActorLogging {
  import JournalSequenceActor._
  import context.dispatcher
  import config.{maxTries, maxBackoffQueryDelay, queryDelay, batchSize}

  override def receive: Receive = receive(0L, Map.empty, 0)

  override def preStart(): Unit = {
    self ! QueryOrderingIds
    val scheduler = context.system.scheduler
    readJournalDao.maxJournalSequence().mapTo[Long].onComplete {
      case scala.util.Success(maxInDatabase) =>
        // All elements smaller than max can be assumed missing after this delay
        val delay = queryDelay * maxTries
        scheduler.scheduleOnce(delay, self, AssumeMaxOrderingId(maxInDatabase))
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
    case AssumeMaxOrderingId(max) =>
      if (currentMaxOrdering < max) {
        context.become(receive(max, missingByCounter, moduloCounter, previousDelay))
      }

    case GetMaxOrderingId =>
      sender() ! MaxOrderingId(currentMaxOrdering)

    case QueryOrderingIds =>
      readJournalDao
        .journalSequence(currentMaxOrdering, batchSize)
        .runWith(Sink.seq)
        .map(result => NewOrderingIds(currentMaxOrdering, result)) pipeTo self

    case NewOrderingIds(originalOffset, _) if originalOffset < currentMaxOrdering =>
      // search was done using an offset that became obsolete in the meantime
      // therefore we start a new query
      self ! QueryOrderingIds

    case NewOrderingIds(_, elements) =>
      findGaps(elements, currentMaxOrdering, missingByCounter, moduloCounter)

    case Status.Failure(t) =>
      val newDelay = maxBackoffQueryDelay.min(previousDelay * 2)
      if (newDelay == maxBackoffQueryDelay) {
        log.warning("Failed to query max ordering id because of {}, retrying in {}", t, newDelay)
      }
      scheduleQuery(newDelay)
      context.become(receive(currentMaxOrdering, missingByCounter, moduloCounter, newDelay))
  }

  /**
   * This method that implements the "find gaps" algo. It's the meat and main purpose of this actor.
   */
  def findGaps(elements: Seq[OrderingId], currentMaxOrdering: OrderingId, missingByCounter: Map[Int, Set[OrderingId]], moduloCounter: Int) = {

    // list of elements that will be considered as genuine gaps.
    // `givenUp` is whether empty or is was filled on a previous iteration
    val givenUp = missingByCounter.getOrElse(moduloCounter, Set.empty)

    val (nextMax, _, missingElems) =
      // using the ordering elements that were fetched, we verify if there are any gaps
      elements.foldLeft[(OrderingId, OrderingId, Set[OrderingId])](currentMaxOrdering, currentMaxOrdering, Set.empty) {

        case ((currentMax, previousElement, missing), currentElement) =>

          // we accumulate in newMissing the gaps we detect on each iteration
          val newMissing = currentElement match {

            // if current element is contiguous to previous, there is no gap
            case e if e == previousElement + 1 => missing

            // if it's a gap and has been detected before on a previous iteration we give up
            // that means that we consider it a genuine gap that will never be filled
            case e if givenUp(e)               => missing

            // any other case is a gap that we expect to be filled soon
            case _ =>
              val currentlyMissing = previousElement + 1 until currentElement
              // we don't want to declare it as missing if it has been already declared on a previous iterations
              def alreadyMissing(e: Long) = missingByCounter.values.exists(_.contains(e))
              missing ++ currentlyMissing.filterNot(alreadyMissing)
          }

          // we must decide if the if we move the cursor forward
          val newMax =
            if ((currentMax + 1).until(currentElement).forall(givenUp.contains)) currentElement
            else currentMax

          (newMax, currentElement, newMissing)
      }

    val newMissingByCounter =
      (missingByCounter + (moduloCounter -> missingElems))
        .map {
          case (key, value) =>
            key -> value.filter(missingId => missingId > nextMax)
        }

    // did we detect gaps in the current batch?
    val noGapsFound = newMissingByCounter.values.forall(_.isEmpty)

    // full batch means that we retrieved as much elements as the batchSize
    // that happens when we are not yet at the end of the stream
    val isFullBatch = elements.size >= batchSize

    if (noGapsFound && isFullBatch) {
      // Many elements have been retrieved but none are missing
      // We can query again immediately, as this allows the actor to rapidly retrieve the real max ordering
      self ! QueryOrderingIds
      context.become(receive(nextMax, newMissingByCounter, moduloCounter))
    } else {
      // whether we detected gaps or we reached the end of stream (batch not full)
      // in this case we want to keep querying but not immediately
      scheduleQuery(queryDelay)
      context.become(receive(nextMax, newMissingByCounter, (moduloCounter + 1) % maxTries))
    }
  }

  var lastScheduledEvent: Option[Cancellable] = None

  def scheduleQuery(delay: FiniteDuration): Unit = {
    lastScheduledEvent = Some(context.system.scheduler.scheduleOnce(delay, self, QueryOrderingIds))
  }

  override def postStop(): Unit = {
    lastScheduledEvent.foreach(_.cancel())
  }
}
