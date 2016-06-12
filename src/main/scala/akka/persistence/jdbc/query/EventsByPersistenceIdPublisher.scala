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

import akka.actor.ActorLogging
import akka.event.LoggingAdapter
import akka.persistence.jdbc.dao.ReadJournalDao
import akka.persistence.jdbc.serialization.SerializationFacade
import akka.persistence.query.EventEnvelope
import akka.persistence.query.journal.leveldb.DeliveryBuffer
import akka.stream.Materializer
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

object EventsByPersistenceIdPublisher {
  sealed trait Command
  case object GetMessages extends Command
  case object BecomePolling extends Command
  case object DetermineSchedulePoll extends Command
}

class EventsByPersistenceIdPublisher(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, readJournalDao: ReadJournalDao, serializationFacade: SerializationFacade, refreshInterval: FiniteDuration, maxBufferSize: Int)(implicit ec: ExecutionContext, mat: Materializer) extends ActorPublisher[EventEnvelope] with DeliveryBuffer[EventEnvelope] with ActorLogging {
  import EventsByPersistenceIdPublisher._
  def determineSchedulePoll(): Unit = {
    if (buf.size < maxBufferSize && totalDemand > 0)
      context.system.scheduler.scheduleOnce(0.seconds, self, BecomePolling)
  }

  val checkPoller = context.system.scheduler.schedule(0.seconds, refreshInterval, self, DetermineSchedulePoll)

  def receive = active(Math.max(1, fromSequenceNr))

  def polling(fromSeqNr: Long): Receive = {
    case GetMessages ⇒
      readJournalDao.messages(persistenceId, fromSeqNr, toSequenceNr, Math.max(0, maxBufferSize - buf.size))
        .take(maxBufferSize - buf.size)
        .via(serializationFacade.deserializeRepr)
        .mapAsync(1)(deserializedRepr ⇒ Future.fromTry(deserializedRepr))
        .map(repr ⇒ EventEnvelope(repr.sequenceNr, repr.persistenceId, repr.sequenceNr, repr.payload))
        .runFold(List.empty[EventEnvelope])(_ :+ _)
        .map { xs ⇒
          buf = buf ++ xs
          val newFromSeqNr = xs.lastOption.map(_.sequenceNr + 1).getOrElse(fromSeqNr)
          deliverBuf()
          context.become(active(newFromSeqNr))
        }
        .recover {
          case t: Throwable ⇒
            log.error(t, "Error while polling eventsByPersistenceIds")
            onError(t)
            context.stop(self)
        }

    case Cancel ⇒ context.stop(self)
  }

  def active(fromSeqNr: Long): Receive = {
    case BecomePolling ⇒
      context.become(polling(fromSeqNr))
      self ! GetMessages

    case DetermineSchedulePoll if buf.size - totalDemand <= 0 ⇒
      determineSchedulePoll()

    case DetermineSchedulePoll if buf.size - totalDemand > 0 ⇒
      deliverBuf()

    case Request(req) ⇒
      deliverBuf()

    case Cancel ⇒ context.stop(self)
  }

  override def postStop(): Unit = {
    checkPoller.cancel()
    super.postStop()
  }
}
