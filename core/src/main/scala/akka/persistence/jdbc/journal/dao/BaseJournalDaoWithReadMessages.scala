/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2025 Lightbend Inc. <https://akka.io>
 */

package akka.persistence.jdbc.journal.dao

import akka.NotUsed
import akka.actor.Scheduler
import akka.persistence.PersistentRepr
import akka.persistence.jdbc.journal.dao.FlowControl.{ Continue, ContinueDelayed, Stop }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success, Try }

trait BaseJournalDaoWithReadMessages extends JournalDaoWithReadMessages {

  implicit val ec: ExecutionContext
  implicit val mat: Materializer

  override def messagesWithBatch(
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long,
      batchSize: Int,
      refreshInterval: Option[(FiniteDuration, Scheduler)]): Source[Try[(PersistentRepr, Long)], NotUsed] = {

    Source
      .unfoldAsync[(Long, FlowControl), Seq[Try[(PersistentRepr, Long)]]]((Math.max(1, fromSequenceNr), Continue)) {
        case (from, control) =>
          def retrieveNextBatch(): Future[Option[((Long, FlowControl), Seq[Try[(PersistentRepr, Long)]])]] = {
            for {
              xs <- messages(persistenceId, from, toSequenceNr, batchSize).runWith(Sink.seq)
            } yield {
              val hasMoreEvents = xs.size == batchSize
              // Events are ordered by sequence number, therefore the last one is the largest)
              val lastSeqNrInBatch: Option[Long] = xs.lastOption match {
                case Some(Success((repr, _))) => Some(repr.sequenceNr)
                case Some(Failure(e))         => throw e // fail the returned Future
                case None                     => None
              }
              val hasLastEvent = lastSeqNrInBatch.exists(_ >= toSequenceNr)
              val nextControl: FlowControl =
                if (hasLastEvent || from > toSequenceNr) Stop
                else if (hasMoreEvents) Continue
                else if (refreshInterval.isEmpty) Stop
                else ContinueDelayed

              val nextFrom: Long = lastSeqNrInBatch match {
                // Continue querying from the last sequence number (the events are ordered)
                case Some(lastSeqNr) => lastSeqNr + 1
                case None            => from
              }
              Some(((nextFrom, nextControl), xs))
            }
          }

          control match {
            case Stop     => Future.successful(None)
            case Continue => retrieveNextBatch()
            case ContinueDelayed =>
              val (delay, scheduler) = refreshInterval.get
              akka.pattern.after(delay, scheduler)(retrieveNextBatch())
          }
      }
      .mapConcat(identity)
  }

}
