/*
 * Copyright 2015 Dennis Vriend
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

package akka.persistence.jdbc.journal

import akka.actor.{ ActorRef, Actor }
import akka.persistence.jdbc.journal.EventsByPersistenceIdRegistry.EventsByPersistenceIdSubscriberTerminated
import akka.persistence.jdbc.serialization.{ SerializationFacade, Serialized }
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.Flow
import scala.collection._
import scala.util.Try

object EventsByPersistenceIdRegistry {
  case class EventsByPersistenceIdSubscriberTerminated(ref: ActorRef)
}

trait EventsByPersistenceIdRegistry { _: SlickAsyncWriteJournal ⇒

  val eventsByPersistenceIdSubscribers = new mutable.HashMap[String, mutable.Set[ActorRef]] with mutable.MultiMap[String, ActorRef]

  val serializationFacade: SerializationFacade = SerializationFacade(context.system)

  //

  def hasEventsByPersistenceIdSubscribers: Boolean = eventsByPersistenceIdSubscribers.nonEmpty

  def addEventsByPersistenceIdSubscribers(subscriber: ActorRef, persistenceId: String): Unit =
    eventsByPersistenceIdSubscribers.addBinding(persistenceId, subscriber)

  def removeEventsByPersistenceIdSubscribers(subscriber: ActorRef): Unit = {
    val keys = eventsByPersistenceIdSubscribers.collect { case (k, s) if s.contains(subscriber) ⇒ k }
    keys.foreach { key ⇒ eventsByPersistenceIdSubscribers.removeBinding(key, subscriber) }
  }

  def eventsByPersistenceIdFlow: Flow[Try[Iterable[Serialized]], Try[Iterable[Serialized]], Unit] =
    Flow[Try[Iterable[Serialized]]].map { atomicWriteResult ⇒
      if (hasEventsByPersistenceIdSubscribers) {
        atomicWriteResult.foreach { (xs: Iterable[Serialized]) ⇒
          val persistenceId = xs.head.persistenceId
          if (eventsByPersistenceIdSubscribers.contains(persistenceId)) {
            eventsByPersistenceIdSubscribers(persistenceId).foreach { subscriber ⇒
              xs.toList.sortBy(_.sequenceNr).foreach { serialized ⇒
                serializationFacade.persistentFromByteArray(serialized.serialized.array()).foreach { repr ⇒
                  val envelope = EventEnvelope(serialized.sequenceNr, serialized.persistenceId, serialized.sequenceNr, repr.payload)
                  subscriber ! JdbcJournal.EventAppended(envelope)
                }
              }
            }
          }
        }
      }
      atomicWriteResult
    }

  def sendEventsByPersistenceIdSubscriberTerminated(ref: ActorRef): Unit =
    self ! EventsByPersistenceIdSubscriberTerminated(ref)

  def receiveEventsByPersistenceIdRegistry: Actor.Receive = {
    case JdbcJournal.EventsByPersistenceIdRequest(persistenceId) ⇒
      addEventsByPersistenceIdSubscribers(sender(), persistenceId)
      context.watch(sender())

    case EventsByPersistenceIdSubscriberTerminated(ref) ⇒
      removeEventsByPersistenceIdSubscribers(ref)
  }
}
