/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.state.scaladsl

import scala.collection.immutable.NumericRange

import akka.actor.{ Actor, ActorLogging, Props, Status, Timers }
import akka.pattern.pipe
import akka.persistence.jdbc.config.DurableStateSequenceRetrievalConfig
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import scala.concurrent.duration.FiniteDuration

object DurableStateSequenceActor {
  def props[A](stateStore: JdbcDurableStateStore[A], config: DurableStateSequenceRetrievalConfig)(
      implicit materializer: Materializer): Props = Props(new DurableStateSequenceActor(stateStore, config))

  case class VisitedElement(pid: PersistenceId, offset: GlobalOffset, revision: Revision) {
    override def toString = s"($pid, $offset, $revision)"
  }
  private case object QueryState
  private case class NewStateInfo(originalOffset: Long, elements: List[VisitedElement])

  private case class ScheduleAssumeMaxGlobalOffset(max: GlobalOffset)
  private case class AssumeMaxGlobalOffset(max: GlobalOffset)

  case object GetMaxGlobalOffset
  case class MaxGlobalOffset(maxOffset: GlobalOffset)

  private case object QueryGlobalOffsetsTimerKey
  private case object AssumeMaxGlobalOffsetTimerKey

  private type GlobalOffset = Long
  private type PersistenceId = String
  private type Revision = Long

  /**
   * Efficient representation of missing elements using NumericRanges.
   * It can be seen as a collection of GlobalOffset
   */
  private case class MissingElements(elements: Seq[NumericRange[GlobalOffset]]) {
    def addRange(from: GlobalOffset, until: GlobalOffset): MissingElements = {
      val newRange = from.until(until)
      MissingElements(elements :+ newRange)
    }
    def contains(id: GlobalOffset): Boolean = elements.exists(_.containsTyped(id))
    def isEmpty: Boolean = elements.forall(_.isEmpty)
    def size: Int = elements.map(_.size).sum
    override def toString: String = {
      elements
        .collect {
          case range if range.nonEmpty =>
            if (range.size == 1) range.start.toString
            else s"${range.start}-${range.end}"
        }
        .mkString(", ")
    }
  }
  private object MissingElements {
    def empty: MissingElements = MissingElements(Vector.empty)
  }
}

/**
 * This actor supports `changesByTag` query to ensure that we don't miss any offsets in the result.
 * In case some offsets are missing we need to re-query (with a delay) and try to fetch the
 * missing offsets. It may be so that the offsets are really missing, in which case we identify them
 * as genuine gaps and continue after `config.maxTries`.
 *
 * There can be three reasons for gaps:
 *
 * 1. The transaction was rolled back. The global offset sequence incremental is not part of the transaction.
 * 2. Global offset is assigned from incrementing a database sequence. The sequence is not part of the
 *    transactions and may result in different order than the commit order. Meaning that in the queries we
 *    may see a later offset before seeing earlier offset. Those missing offsets will be seen when we
 *    re-query. See further explanation in for example
 *    https://espadrine.github.io/blog/posts/two-postgresql-sequence-misconceptions.html
 * 3. There are multiple updates (revisions) to the same persistence id and the queries may only see the
 *    latest revision. Meaning that the additional earlier revisions will be seen as offset gaps.
 *
 * If offset gaps have been detected we try to confirm the gaps by looking at revision changes of
 * individual persistence ids. We keep a cache of previously known revision per persistence ids.
 * If the total number of revision changes corresponds to the number of missing offsets they are
 * considered confirmed to be from case 3 and we can continue without re-query delays.
 *
 * Note that if we have seen revision 10 of p6 and we retrieve revision 13 of p6, we also know that there have been
 * revision 11 and 12 of p6. We are using READ COMMITTED transaction isolation level and we have a check of
 * sequentiality of revisions in `upsert` implementation.
 *
 * We have to delay and re-query for new persistence ids with revision > 1 that we don't know the previous revision,
 * because that could be gaps from case 1 or 2.
 *
 * If gaps cannot be confirmed it will re-query up to `config.maxTries` times before giving up and continue with
 * the highest offset. For example case 1.
 */
class DurableStateSequenceActor[A](stateStore: JdbcDurableStateStore[A], config: DurableStateSequenceRetrievalConfig)(
    implicit materializer: Materializer)
    extends Actor
    with ActorLogging
    with Timers {
  import DurableStateSequenceActor._
  import context.dispatcher
  import config.{ batchSize, maxBackoffQueryDelay, maxTries, queryDelay, revisionCacheCapacity }

  private val revisionCache = collection.mutable.Map.empty[PersistenceId, VisitedElement]

  override def receive: Receive = receive(0L, Map.empty, 0)

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
   * @param currentMaxGlobalOffset The highest offset value for which it is known that no missing elements exist
   * @param missingByCounter A map with missing offsets. The key of the map is the count at which the missing elements
   *                         can be assumed to be "skipped ids" (they are no longer assumed missing). Used together
   *                         with the `moduloCounter` to implement a "sliding window" where missing offsets are
   *                         re-tried up to `maxTries` before assumed ok.
   * @param moduloCounter A counter which is incremented every time a new query have been executed, modulo `maxTries`
   * @param previousDelay The last used delay (may change in case failures occur)
   */
  final def receive(
      currentMaxGlobalOffset: GlobalOffset,
      missingByCounter: Map[Int, MissingElements],
      moduloCounter: Int,
      previousDelay: FiniteDuration = queryDelay): Receive = {
    case ScheduleAssumeMaxGlobalOffset(max) =>
      // All elements smaller than max can be assumed missing after this delay
      val delay = queryDelay * maxTries
      timers.startSingleTimer(key = AssumeMaxGlobalOffsetTimerKey, AssumeMaxGlobalOffset(max), delay)

    case AssumeMaxGlobalOffset(max) =>
      if (currentMaxGlobalOffset < max) {
        context.become(receive(max, missingByCounter, moduloCounter, previousDelay))
      }

    case GetMaxGlobalOffset =>
      sender() ! MaxGlobalOffset(currentMaxGlobalOffset)

    case QueryState =>
      stateStore
        .stateStoreStateInfo(currentMaxGlobalOffset, batchSize)
        .runWith(Sink.seq)
        .map(result =>
          NewStateInfo(
            currentMaxGlobalOffset,
            result.map { case (pid, offset, rev) =>
              VisitedElement(pid, offset, rev)
            }.toList))
        .pipeTo(self)

    case NewStateInfo(originalOffset, _) if originalOffset < currentMaxGlobalOffset =>
      // search was done using an offset that became obsolete in the meantime
      // therefore we start a new query
      self ! QueryState

    case NewStateInfo(_, elements) =>
      findGaps(elements, currentMaxGlobalOffset, missingByCounter, moduloCounter)

    case Status.Failure(t) =>
      val newDelay = maxBackoffQueryDelay.min(previousDelay * 2)
      if (newDelay == maxBackoffQueryDelay) {
        log.warning("Failed to query max global offset because of {}, retrying in [{}]", t, newDelay.toCoarsest)
      }
      scheduleQuery(newDelay)
      context.become(receive(currentMaxGlobalOffset, missingByCounter, moduloCounter, newDelay))
  }

  /**
   * This method that implements the "find gaps" algo. It's the meat and main purpose of this actor.
   */
  final def findGaps(
      elements: List[VisitedElement],
      currentMaxOffset: GlobalOffset,
      missingByCounter: Map[Int, MissingElements],
      moduloCounter: Int): Unit = {
    // list of elements that will be considered as genuine gaps.
    // `givenUp` is either empty or is was filled on a previous iteration
    val givenUp = missingByCounter.getOrElse(moduloCounter, MissingElements.empty)

    val (nextMax, _, missingElems) =
      // using the global offset of the elements that were fetched, we verify if there are any gaps
      elements.foldLeft[(GlobalOffset, GlobalOffset, MissingElements)](
        (currentMaxOffset, currentMaxOffset, MissingElements.empty)) {
        case ((currentMax, previousOffset, missing), currentElement) =>
          // we must decide if we move the cursor forward
          val newMax =
            if ((currentMax + 1).until(currentElement.offset).forall(givenUp.contains)) {
              // we move the cursor forward when:
              // 1) they have been detected as missing on previous iteration, it's time now to give up
              // 2) current + 1 == currentElement (meaning no gap). Note that `forall` on an empty range always returns true
              currentElement.offset
            } else currentMax

          // we accumulate in newMissing the gaps we detect on each iteration
          val newMissing =
            if (previousOffset + 1 == currentElement.offset || newMax == currentElement.offset) missing
            else missing.addRange(previousOffset + 1, currentElement.offset)

          (newMax, currentElement.offset, newMissing)
      }

    // these offsets will be used as givenUp after one round when back to the same  moduloCounter
    val newMissingByCounter = missingByCounter + (moduloCounter -> missingElems)

    // did we detect gaps in the current batch?
    val noGapsFound = missingElems.isEmpty

    // full batch means that we retrieved as much elements as the batchSize
    // that happens when we are not yet at the end of the stream
    val isFullBatch = elements.size == batchSize

    if (noGapsFound) {
      addToRevisionCache(elements, nextMax)
      if (isFullBatch) {
        // We can query again immediately, as this allows the actor to rapidly retrieve the real max offset.
        // Using same moduloCounter.
        self ! QueryState
        context.become(receive(nextMax, newMissingByCounter, moduloCounter))
      } else {
        // keep querying but not immediately
        scheduleQuery(queryDelay)
        context.become(receive(nextMax, newMissingByCounter, (moduloCounter + 1) % maxTries))
      }
    } else {
      // We detected gaps. When there are updates to the same persistence id we might not see all subsequent
      // changes but only the latest. Those changes will be seen as gaps. By looking at the difference in revisions
      // for persistence ids that we have seen before (included in the revisionCache) we try to confirm if
      // the offset gaps can be filled by the revision changes.
      val missingOffsetCount = missingElems.size

      val (inBetweenRevisionChanges, newMaxOffset, cacheMissed) =
        // in this fold we find the possibly new max offset and the total revision difference for all persistence ids
        elements.foldLeft((0L, nextMax, false)) { case ((revChg, currMaxOffset, cacheMiss), elem) =>
          revisionCache.get(elem.pid) match {
            case Some(e) =>
              // cache hit: find the revision difference
              val maxOffset = math.max(currMaxOffset, elem.offset)
              val revDiff = elem.revision - e.revision
              if (revDiff <= 1) {
                (revChg, maxOffset, cacheMiss)
              } else {
                val pidOffsets =
                  (e.offset until elem.offset).tail // e.offset and elem.offset are known to not be missing
                val missingCount = math.min(pidOffsets.count(missingElems.contains), revDiff - 1)
                (revChg + missingCount, maxOffset, cacheMiss)
              }
            case None =>
              // this persistence id was not present in the cache
              (revChg, math.max(elem.offset, currMaxOffset), cacheMiss || elem.revision != 1L)
          }
        }

      // in this case we want to keep querying but not immediately
      scheduleQuery(queryDelay)

      if (cacheMissed || missingOffsetCount != inBetweenRevisionChanges) {
        // gaps could not be confirmed

        if (log.isDebugEnabled) {
          log.debug(
            "Offset gaps detected [{}]. Current max offset [{}]. [{}] gaps could not be confirmed by revision changes.{}",
            missingElems,
            nextMax,
            missingOffsetCount - inBetweenRevisionChanges,
            if (cacheMissed) " Some new persistence ids without previously known revision." else "")
        }

        addToRevisionCache(elements, nextMax)
        context.become(receive(nextMax, newMissingByCounter, (moduloCounter + 1) % maxTries))
      } else {
        addToRevisionCache(elements, newMaxOffset)
        context.become(receive(newMaxOffset, newMissingByCounter, (moduloCounter + 1) % maxTries))
      }
    }
  }

  private def addToRevisionCache(elements: List[VisitedElement], upToOffset: GlobalOffset): Unit = {
    revisionCache ++= elements.iterator.collect { case e if e.offset <= upToOffset => e.pid -> e }
    evictRevisionCacheIfNeeded()
  }

  private def evictRevisionCacheIfNeeded(): Unit = {
    def divRoundUp(num: Int, divisor: Int): Int = (num + divisor - 1) / divisor

    if (revisionCache.size > revisionCacheCapacity) {
      val sortedEntries = revisionCache.toVector.sortBy { case (_, elem) => elem.offset }
      // keep 90% of capacity
      val numberOfEntriesToRemove = (sortedEntries.size - revisionCacheCapacity) + divRoundUp(revisionCacheCapacity, 10)
      revisionCache --= sortedEntries.iterator.take(numberOfEntriesToRemove).map(_._1)
    }
  }

  def scheduleQuery(delay: FiniteDuration): Unit = {
    timers.startSingleTimer(key = QueryGlobalOffsetsTimerKey, QueryState, delay)
  }
}
