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

package akka.persistence.jdbc.journal

import akka.actor.{ Actor, ActorRef }
import akka.persistence.AtomicWrite
import akka.persistence.jdbc.journal.EventsByPersistenceIdRegistry.EventsByPersistenceIdSubscriberTerminated
import akka.persistence.jdbc.serialization.Serialized
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.Flow

import scala.collection._
import scala.util.Try

object EventsByPersistenceIdRegistry {
  case class EventsByPersistenceIdSubscriberTerminated(ref: ActorRef)
}

trait EventsByPersistenceIdRegistry { _: SlickAsyncWriteJournal ⇒

  private val eventsByPersistenceIdSubscribers = new mutable.HashMap[String, mutable.Set[ActorRef]] with mutable.MultiMap[String, ActorRef]

  protected def hasEventsByPersistenceIdSubscribers: Boolean = eventsByPersistenceIdSubscribers.nonEmpty

  private def addEventsByPersistenceIdSubscriber(subscriber: ActorRef, persistenceId: String): Unit =
    eventsByPersistenceIdSubscribers.addBinding(persistenceId, subscriber)

  private def removeEventsByPersistenceIdSubscriber(subscriber: ActorRef): Unit = {
    val keys = eventsByPersistenceIdSubscribers.collect { case (k, s) if s.contains(subscriber) ⇒ k }
    keys.foreach { key ⇒ eventsByPersistenceIdSubscribers.removeBinding(key, subscriber) }
  }

  protected def sendEventsByPersistenceIdSubscriberTerminated(ref: ActorRef): Unit =
    self ! EventsByPersistenceIdSubscriberTerminated(ref)

  protected def receiveEventsByPersistenceIdRegistry: Actor.Receive = {
    case JdbcJournal.EventsByPersistenceIdRequest(persistenceId) ⇒
      addEventsByPersistenceIdSubscriber(sender(), persistenceId)
      context.watch(sender())

    case EventsByPersistenceIdSubscriberTerminated(ref) ⇒
      removeEventsByPersistenceIdSubscriber(ref)
  }

  protected def eventsByPersistenceIdFlow(atomicWrites: Iterable[AtomicWrite]): Flow[Try[Iterable[Serialized]], Try[Iterable[Serialized]], Unit] =
    Flow[Try[Iterable[Serialized]]].map { atomicWriteResult ⇒
      if (hasEventsByPersistenceIdSubscribers) {
        for {
          seqSerialized ← atomicWriteResult
          headOfSeqSerialized ← seqSerialized.headOption
          persistenceId = headOfSeqSerialized.persistenceId
          if eventsByPersistenceIdSubscribers contains persistenceId
          atomicWrite ← atomicWrites
          if atomicWrite.persistenceId == persistenceId
          subscriber ← eventsByPersistenceIdSubscribers(persistenceId)
          persistentRepr ← atomicWrite.payload
          envelope = EventEnvelope(persistentRepr.sequenceNr, persistentRepr.persistenceId, persistentRepr.sequenceNr, persistentRepr.payload)
          eventAppended = JdbcJournal.EventAppended(envelope)
        } subscriber ! eventAppended
      }
      atomicWriteResult
    }
}
