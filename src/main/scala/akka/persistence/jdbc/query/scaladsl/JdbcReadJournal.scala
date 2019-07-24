/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.jdbc.query
package scaladsl

import akka.NotUsed
import akka.actor.ExtendedActorSystem
import akka.persistence.jdbc.config.ReadJournalConfig
import akka.persistence.jdbc.query.JournalSequenceActor.{ GetMaxOrderingId, MaxOrderingId }
import akka.persistence.jdbc.query.dao.ReadJournalDao
import akka.persistence.jdbc.util.SlickExtension
import akka.persistence.query.scaladsl._
import akka.persistence.query.{ EventEnvelope, Offset, Sequence }
import akka.persistence.{ Persistence, PersistentRepr }
import akka.serialization.{ Serialization, SerializationExtension }
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.Timeout
import com.typesafe.config.Config
import slick.jdbc.JdbcBackend._
import slick.jdbc.JdbcProfile

import scala.collection.immutable._
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object JdbcReadJournal {
  final val Identifier = "jdbc-read-journal"

  private sealed trait FlowControl

  /** Keep querying - used when we are sure that there is more events to fetch */
  private case object Continue extends FlowControl

  /**
   * Keep querying with delay - used when we have consumed all events,
   * but want to poll for future events
   */
  private case object ContinueDelayed extends FlowControl

  /** Stop querying - used when we reach the desired offset  */
  private case object Stop extends FlowControl
}

class JdbcReadJournal(config: Config, configPath: String)(implicit val system: ExtendedActorSystem) extends ReadJournal
  with CurrentPersistenceIdsQuery
  with PersistenceIdsQuery
  with CurrentEventsByPersistenceIdQuery
  with EventsByPersistenceIdQuery
  with CurrentEventsByTagQuery
  with EventsByTagQuery {

  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = ActorMaterializer()
  val readJournalConfig = new ReadJournalConfig(config)

  private val writePluginId = config.getString("write-plugin")
  private val eventAdapters = Persistence(system).adaptersFor(writePluginId)

  val readJournalDao: ReadJournalDao = {
    val slickDb = SlickExtension(system).database(config)
    val db = slickDb.database
    if (readJournalConfig.addShutdownHook && slickDb.allowShutdown) {
      system.registerOnTermination {
        db.close()

      }
    }
    val fqcn = readJournalConfig.pluginConfig.dao
    val profile: JdbcProfile = slickDb.profile
    val args = Seq(
      (classOf[Database], db),
      (classOf[JdbcProfile], profile),
      (classOf[ReadJournalConfig], readJournalConfig),
      (classOf[Serialization], SerializationExtension(system)),
      (classOf[ExecutionContext], ec),
      (classOf[Materializer], mat))
    system.dynamicAccess.createInstanceFor[ReadJournalDao](fqcn, args) match {
      case Success(dao)   => dao
      case Failure(cause) => throw cause
    }
  }

  // Started lazily to prevent the actor for querying the db if no eventsByTag queries are used
  private[query] lazy val journalSequenceActor = system.systemActorOf(
    JournalSequenceActor.props(readJournalDao, readJournalConfig.journalSequenceRetrievalConfiguration),
    s"$configPath.akka-persistence-jdbc-journal-sequence-actor")
  private val delaySource =
    Source.tick(readJournalConfig.refreshInterval, 0.seconds, 0).take(1)

  override def currentPersistenceIds(): Source[String, NotUsed] =
    readJournalDao.allPersistenceIdsSource(Long.MaxValue)

  override def persistenceIds(): Source[String, NotUsed] =
    Source.repeat(0).flatMapConcat(_ => delaySource.flatMapConcat(_ => currentPersistenceIds()))
      .statefulMapConcat[String] { () =>
        var knownIds = Set.empty[String]
        def next(id: String): Iterable[String] = {
          val xs = Set(id).diff(knownIds)
          knownIds += id
          xs
        }
        (id) => next(id)
      }

  private def adaptEvents(repr: PersistentRepr): Seq[PersistentRepr] = {
    val adapter = eventAdapters.get(repr.payload.getClass)
    adapter.fromJournal(repr.payload, repr.manifest).events.map(repr.withPayload)
  }

  private def currentJournalEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[PersistentRepr, NotUsed] =
    readJournalDao.messages(persistenceId, fromSequenceNr, toSequenceNr, Long.MaxValue)
      .mapAsync(1)(deserializedRepr => Future.fromTry(deserializedRepr))

  override def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    currentJournalEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr)
      .mapConcat(adaptEvents)
      .map(repr => EventEnvelope(Sequence(repr.sequenceNr), repr.persistenceId, repr.sequenceNr, repr.payload))

  override def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    Source.unfoldAsync[Long, Seq[EventEnvelope]](Math.max(1, fromSequenceNr)) { (from: Long) =>
      def nextFromSeqNr(xs: Seq[EventEnvelope]): Long = {
        if (xs.isEmpty) from else xs.map(_.sequenceNr).max + 1
      }
      from match {
        case x if x > toSequenceNr => Future.successful(None)
        case _ =>
          delaySource
            .flatMapConcat { _ =>
              currentJournalEventsByPersistenceId(persistenceId, from, from + (readJournalConfig.maxBufferSize - 1))
            }
            .mapConcat(adaptEvents)
            .map(repr => EventEnvelope(Sequence(repr.sequenceNr), repr.persistenceId, repr.sequenceNr, repr.payload))
            .runWith(Sink.seq).map { xs =>
              val newFromSeqNr = nextFromSeqNr(xs)
              Some((newFromSeqNr, xs))
            }
      }
    }.mapConcat(identity)

  /**
   * Same type of query as [[EventsByTagQuery#eventsByTag]] but the event stream
   * is completed immediately when it reaches the end of the "result set". Events that are
   * stored after the query is completed are not included in the event stream.
   *
   * akka-persistence-jdbc has implemented this feature by using a LIKE %tag% query on the tags column.
   * A consequence of this is that tag names must be chosen wisely: for example when querying the tag `User`,
   * events with the tag `UserEmail` will also be returned (since User is a substring of UserEmail).
   *
   * The returned event stream is ordered by `offset`.
   */
  override def currentEventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] =
    currentEventsByTag(tag, offset.value)

  private def currentJournalEventsByTag(tag: String, offset: Long, max: Long, latestOrdering: MaxOrderingId): Source[EventEnvelope, NotUsed] = {
    if (latestOrdering.maxOrdering < offset) Source.empty
    else {
      readJournalDao.eventsByTag(tag, offset, latestOrdering.maxOrdering, max)
        .mapAsync(1)(Future.fromTry)
        .mapConcat {
          case (repr, _, ordering) =>
            adaptEvents(repr).map(r => EventEnvelope(Sequence(ordering), r.persistenceId, r.sequenceNr, r.payload))
        }
    }
  }

  /**
   * @param terminateAfterOffset If None, the stream never completes. If a Some, then the stream will complete once a
   *                             query has been executed which might return an event with this offset (or a higher offset).
   *                             The stream may include offsets higher than the value in terminateAfterOffset, since the last batch
   *                             will be returned completely.
   */
  private def eventsByTag(tag: String, offset: Long, terminateAfterOffset: Option[Long]): Source[EventEnvelope, NotUsed] = {

    import akka.pattern.ask
    import JdbcReadJournal._
    implicit val askTimeout: Timeout = Timeout(readJournalConfig.journalSequenceRetrievalConfiguration.askTimeout)
    val batchSize = readJournalConfig.maxBufferSize

    Source.unfoldAsync[(Long, FlowControl), Seq[EventEnvelope]]((offset, Continue)) {
      case (from, control) =>
        def retrieveNextBatch() = {
          for {
            queryUntil <- journalSequenceActor.ask(GetMaxOrderingId).mapTo[MaxOrderingId]
            xs <- currentJournalEventsByTag(tag, from, batchSize, queryUntil).runWith(Sink.seq)
          } yield {

            val hasMoreEvents = xs.size == batchSize
            val control =
              terminateAfterOffset match {
                // we may stop if target is behind queryUntil and we don't have more events to fetch
                case Some(target) if !hasMoreEvents && target <= queryUntil.maxOrdering => Stop
                // We may also stop if we have found an event with an offset >= target
                case Some(target) if xs.exists(_.offset.value >= target)                => Stop

                // otherwise, disregarding if Some or None, we must decide how to continue
                case _ =>
                  if (hasMoreEvents) Continue else ContinueDelayed
              }

            val nextStartingOffset = if (xs.isEmpty) {
              /* If no events matched the tag between `from` and `maxOrdering` then there is no need to execute the exact
               * same query again. We can continue querying from `maxOrdering`, which will save some load on the db.
               * (Note: we may never return a value smaller than `from`, otherwise we might return duplicate events) */
              math.max(from, queryUntil.maxOrdering)
            } else {
              // Continue querying from the largest offset
              xs.map(_.offset.value).max
            }
            Some((nextStartingOffset, control), xs)
          }
        }

        control match {
          case Stop     => Future.successful(None)
          case Continue => retrieveNextBatch()
          case ContinueDelayed =>
            akka.pattern.after(readJournalConfig.refreshInterval, system.scheduler)(retrieveNextBatch())
        }

    }.mapConcat(identity)
  }

  def currentEventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    Source.fromFuture(readJournalDao.maxJournalSequence())
      .flatMapConcat { maxOrderingInDb =>
        eventsByTag(tag, offset, terminateAfterOffset = Some(maxOrderingInDb))
      }

  /**
   * Query events that have a specific tag.
   *
   * akka-persistence-jdbc has implemented this feature by using a LIKE %tag% query on the tags column.
   * A consequence of this is that tag names must be chosen wisely: for example when querying the tag `User`,
   * events with the tag `UserEmail` will also be returned (since User is a substring of UserEmail).
   *
   * The consumer can keep track of its current position in the event stream by storing the
   * `offset` and restart the query from a given `offset` after a crash/restart.
   *
   * For akka-persistence-jdbc the `offset` corresponds to the `ordering` column in the Journal table.
   * The `ordering` is a sequential id number that uniquely identifies the position of each event within
   * the event stream.
   *
   * The returned event stream is ordered by `offset`.
   *
   * The stream is not completed when it reaches the end of the currently stored events,
   * but it continues to push new events when new events are persisted.
   * Corresponding query that is completed when it reaches the end of the currently
   * stored events is provided by [[CurrentEventsByTagQuery#currentEventsByTag]].
   */
  override def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] =
    eventsByTag(tag, offset.value)

  def eventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    eventsByTag(tag, offset, terminateAfterOffset = None)
}
