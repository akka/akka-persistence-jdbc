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
import akka.event.LoggingReceive
import akka.persistence.jdbc.dao.ReadJournalDao
import akka.persistence.query.journal.leveldb.DeliveryBuffer
import akka.stream.Materializer
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object AllPersistenceIdsPublisher {
  sealed trait Command
  case object GetAllPersistenceIds extends Command
  case object BecomePolling extends Command
  case object DetermineSchedulePoll extends Command
}
class AllPersistenceIdsPublisher(readJournalDao: ReadJournalDao, refreshInterval: FiniteDuration, maxBufferSize: Int)(implicit ec: ExecutionContext, mat: Materializer) extends ActorPublisher[String] with DeliveryBuffer[String] with ActorLogging {
  import AllPersistenceIdsPublisher._
  def determineSchedulePoll(): Unit = {
    if (buf.size < maxBufferSize && totalDemand > 0)
      context.system.scheduler.scheduleOnce(0.seconds, self, BecomePolling)
  }

  val checkPoller = context.system.scheduler.schedule(0.seconds, refreshInterval, self, DetermineSchedulePoll)

  def receive = active(Set.empty[String])

  /**
   * Will only handle GetAllPersistenceIds and Cancel messages,
   * as they will not change the state.
   */
  def polling(knownIds: Set[String]): Receive = LoggingReceive {
    case GetAllPersistenceIds ⇒
      readJournalDao.allPersistenceIdsSource(Math.max(0, maxBufferSize - buf.size)).runFold(List.empty[String])(_ :+ _).map { ids ⇒
        val xs = ids.toSet.diff(knownIds).toVector
        buf = buf ++ xs
        deliverBuf()
        context.become(active(knownIds ++ xs))
      }.recover {
        case t: Throwable ⇒
          log.error(t, "Error while polling allPersistenceIds")
          onError(t)
          context.stop(self)
      }

    case Cancel ⇒ context.stop(self)
  }

  def active(knownIds: Set[String]): Receive = LoggingReceive {
    case BecomePolling ⇒
      context.become(polling(knownIds))
      self ! GetAllPersistenceIds

    case DetermineSchedulePoll if buf.size - totalDemand <= 0 ⇒ determineSchedulePoll()

    case DetermineSchedulePoll                                ⇒ deliverBuf()

    case Request(req)                                         ⇒ deliverBuf()

    case Cancel                                               ⇒ context.stop(self)
  }

  override def postStop(): Unit = {
    checkPoller.cancel()
    super.postStop()
  }
}
