/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.state.scaladsl

import akka.actor.{ Actor, ActorLogging, Props, Status, Timers }
import akka.pattern.pipe
import akka.persistence.jdbc.config.DurableStateSequenceRetrievalConfig
import akka.stream.Materializer
import akka.stream.scaladsl.Sink

import scala.concurrent.duration.FiniteDuration

object DurableStateSequenceActor {
  def props[A](stateStore: JdbcDurableStateStore[A], config: DurableStateSequenceRetrievalConfig)(
      implicit materializer: Materializer): Props = Props(new DurableStateSequenceActor(stateStore, config))

  case class VisitedElement(pid: PersistenceId, offset: GlobalOffset, revision: Revision)
  private case object QueryState
  private case class NewStateInfo(originalOffset: Long, elements: List[VisitedElement])

  private case class ScheduleAssumeMaxGlobalOffset(max: GlobalOffset)
  private case class AssumeMaxOrderingId(max: GlobalOffset)

  case object GetMaxGlobalOffset
  case class MaxGlobalOffset(maxOrdering: GlobalOffset)

  private case object QueryGlobalOffsetsTimerKey
  private case object AssumeMaxGlobalOffsetTimerKey

  private type GlobalOffset = Long
  private type PersistenceId = String
  private type Revision = Long
}

/**
 * This actor supports `changesByTag` query to ensure that we don't miss any offsets in the result.
 * In case some offsets are missing we need to re-query (may be with a delay) and try to fetech the
 * missing offsets. It may be so that the offsets are really missing, in which case we identify them
 * as gaps.
 *
 * We use the `revision` field as the one to identify missing offsets. e.g. if we have the triplet
 * of (pid, offset, revision) as (p1, 10, 10), this implies that we do not miss any offset for p1,
 * since the 10 revisions account for the increase of offset from 1 to 10.
 *
 * However in reality, the situation is more complex, as we will be having concurrent upserts and hence
 * non-sequential offsets for a pid. In that case we need to consider all the triplets fetched in the query
 * at once, consider the total revisions between them and try to distribute those revisions amongst the offset
 * gaps. Here's an example:
 *
 * Suppose the fetched result from the query has the following triplets of (persistence_id, offset, revision) :
 *
 *   ("p6", 54, 10),
 *   ("p2", 56, 10),
 *   ("p1", 57, 10),
 *   ("p7", 58, 10),
 *   ("p3", 59, 10),
 *   ("p4", 60, 10),
 *   ("p5", 70, 10))
 *
 * Here each persistence id has 10 revisions, which makes 70 as the total number of revisions. And the max offset
 * is also 70, which makes a perfect fit for the range without missing any offset. Also note that since we have
 * seen revision 10 of p6, we can also ensure that we have seen revision 9 since we are using READ COMMITTED transaction
 * isolation level and we have a check of sequentiality of revisions in `upsert` implementation.
 *
 * Requerying - we need to requery in the following circumstances:
 *
 * a. In order to get past offsets and revisions seen for a persistence id, we maintain a cache. If a persistence id
 *    is not found in the cache we use the defensive approach of delay and re-query.
 * b. We have a predefined batch size to fetch. If the query returns a result set of size < the batch size we do a
 *    delayed re-query
 * c. If we detect missing offsets we also do delayed re-query
 *
 * All re-queries are done till a max number (pre-defined) is reached. At that point we give up and return the result.
 */
class DurableStateSequenceActor[A](stateStore: JdbcDurableStateStore[A], config: DurableStateSequenceRetrievalConfig)(
    implicit materializer: Materializer)
    extends Actor
    with ActorLogging
    with Timers {
  import DurableStateSequenceActor._
  import context.dispatcher
  import config.{ batchSize, maxBackoffQueryDelay, maxTries, queryDelay }

  // result set of last query
  // do we need eviction ?
  private val lastQueryResult = collection.mutable.Map.empty[PersistenceId, VisitedElement]

  override def receive: Receive = receive(0L, 0, 0)

  override def preStart(): Unit = {
    self ! QueryState
    stateStore.maxStateStoreOffset().mapTo[Long].onComplete {
      case scala.util.Success(maxInDatabase) =>
        self ! ScheduleAssumeMaxGlobalOffset(maxInDatabase)
      case scala.util.Failure(t) =>
        log.info("Failed to recover fast, using state-by-state recovery instead. Cause: {}", t)
    }
  }

  /**
   * @param currentMaxOrdering The highest ordering value for which it is known that no missing elements exist
   * @param missingByCounter A map with missing orderingIds. The key of the map is the count at which the missing elements
   *                         can be assumed to be "skipped ids" (they are no longer assumed missing).
   * @param moduloCounter A counter which is incremented every time a new query have been executed, modulo `maxTries`
   * @param previousDelay The last used delay (may change in case failures occur)
   */
  final def receive(
      currentMaxGlobalOffset: GlobalOffset,
      missingOffsetCount: Long,
      moduloCounter: Int,
      previousDelay: FiniteDuration = queryDelay): Receive = {
    case ScheduleAssumeMaxGlobalOffset(max) =>
      // All elements smaller than max can be assumed missing after this delay
      val delay = queryDelay * maxTries
      timers.startSingleTimer(key = AssumeMaxGlobalOffsetTimerKey, AssumeMaxOrderingId(max), delay)

    case AssumeMaxOrderingId(max) =>
      if (currentMaxGlobalOffset < max) {
        context.become(receive(max, missingOffsetCount, moduloCounter, previousDelay))
      }

    case GetMaxGlobalOffset =>
      sender() ! MaxGlobalOffset(currentMaxGlobalOffset)

    case QueryState =>
      stateStore
        .stateStoreStateInfo(currentMaxGlobalOffset, batchSize)
        .runWith(Sink.seq)
        .map(result => NewStateInfo(currentMaxGlobalOffset, result.map(e => VisitedElement(e._1, e._2, e._3)).toList))
        .pipeTo(self)

    case NewStateInfo(originalOffset, _) if originalOffset < currentMaxGlobalOffset =>
      // search was done using an offset that became obsolete in the meantime
      // therefore we start a new query
      self ! QueryState

    case NewStateInfo(_, elements) =>
      findGaps(elements, currentMaxGlobalOffset, missingOffsetCount, moduloCounter)

    case Status.Failure(t) =>
      val newDelay = maxBackoffQueryDelay.min(previousDelay * 2)
      if (newDelay == maxBackoffQueryDelay) {
        log.warning("Failed to query max global offset because of {}, retrying in {}", t, newDelay)
      }
      scheduleQuery(newDelay)
      context.become(receive(currentMaxGlobalOffset, missingOffsetCount, moduloCounter, newDelay))
  }

  /**
   * This method that implements the "find gaps" algo. It's the meat and main purpose of this actor.
   */
  final def findGaps(
      elements: List[VisitedElement],
      currentMaxGlobalOffset: GlobalOffset,
      missingOffsetCount: Long,
      moduloCounter: Int): Unit = {

    val (missingOffsetCurrentCount, nextMax, cacheMissed) = findRequeryCriteria(elements, currentMaxGlobalOffset)

    // full batch means that we retrieved as much elements as the batchSize
    // that happens when we are not yet at the end of the stream
    val isFullBatch = elements.size == batchSize

    if (missingOffsetCount + missingOffsetCurrentCount == 0 && isFullBatch && !cacheMissed) {
      // Many elements have been retrieved but none are missing (no gaps)
      // We can query again immediately, as this allows the actor to rapidly retrieve the real max ordering
      self ! QueryState
      context.become(receive(nextMax, missingOffsetCount + missingOffsetCurrentCount, moduloCounter))
    } else {
      // 3 conditions where we need to delay and re-query
      //
      // 1. we detected gaps
      // 2. we reached the end of stream (batch not full)
      // 3. we encountered cache miss, i.e. found a new pid not present in the cache. This is because if the
      //    revision of this newly encountered pid is > 1 we need to see the revision 1 (which may be very old)
      //    in order to find if it's still relevant
      // in this case we want to keep querying but not immediately
      scheduleQuery(queryDelay)
      context.become(receive(nextMax, missingOffsetCount + missingOffsetCurrentCount, (moduloCounter + 1) % maxTries))
    }

    // cache to be used in next iteration
    if (!elements.isEmpty) {
      elements.foreach(e => lastQueryResult += ((e.pid, e)))
    }
  }

  /**
   * Find missing offset count and the new max offset by traversing through the revision
   * changes across all persistence ids
   *
   * @param current List of elements fetched by the query
   * @param previousMaxOffset Max global offset from the previous query
   * @return a Tuple2 of (missing-offset-count, current-max-offset, if-we-got-a-cache-miss)
   */
  private final def findRequeryCriteria(
      current: List[VisitedElement],
      previousMaxOffset: GlobalOffset): (Long, GlobalOffset, Boolean) = {
    val (totalRevisionChangesSinceLastQuery, newMaxOffset, cacheMissed) =
      // in this fold we find the new max offset and the total revision difference for
      // all persistence ids compared to the last query result
      current.foldLeft((0L, previousMaxOffset, false)) { (acc, elem) =>

        lastQueryResult
          .get(elem.pid)
          .map { e =>
            // last result set cache hit : find the revision difference
            val revDiff = elem.revision - e.revision
            val maxOffset = math.max(acc._2, elem.offset)
            (acc._1 + revDiff, maxOffset, acc._3 || false)

          }
          .getOrElse {
            // this persistence id was not present in the last result set
            (acc._1 + elem.revision, math.max(elem.offset, acc._2), acc._3 || true)
          }
      }
    // missing offset count
    val missingOffsetCount = totalRevisionChangesSinceLastQuery - (newMaxOffset - previousMaxOffset)
    (missingOffsetCount, newMaxOffset, cacheMissed)
  }

  def scheduleQuery(delay: FiniteDuration): Unit = {
    timers.startSingleTimer(key = QueryGlobalOffsetsTimerKey, QueryState, delay)
  }
}
