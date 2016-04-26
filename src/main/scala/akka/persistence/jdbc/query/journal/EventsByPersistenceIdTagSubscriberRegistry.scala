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

package akka.persistence.jdbc.query.journal

import akka.NotUsed
import akka.actor.{ Actor, ActorRef }
import akka.persistence.AtomicWrite
import akka.persistence.jdbc.journal.SlickAsyncWriteJournal
import akka.persistence.jdbc.query.journal.scaladsl.JdbcReadJournal
import akka.persistence.jdbc.serialization.SerializationResult
import akka.persistence.journal.Tagged
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.Flow

import scala.collection.{ Iterable, mutable }
import scala.util.Try

object EventsByPersistenceIdTagSubscriberRegistry {
  case class EventsByPersistenceIdTagSubscriberTerminated(ref: ActorRef)
}

trait EventsByPersistenceIdTagSubscriberRegistry { _: SlickAsyncWriteJournal ⇒
  import EventsByPersistenceIdTagSubscriberRegistry._
  private val eventsByPersistenceIdAndTagSubscribers = new mutable.HashMap[String, mutable.Set[ActorRef]] with mutable.MultiMap[String, ActorRef]

  private def hasEventsByTagSubscribers: Boolean = eventsByPersistenceIdAndTagSubscribers.nonEmpty

  def subscriberKey(persistenceId: String, tag: String): String = s"$persistenceId-$tag"

  private def addEventsByTagSubscriber(subscriber: ActorRef, persistenceId: String, tag: String): Unit =
    eventsByPersistenceIdAndTagSubscribers.addBinding(subscriberKey(persistenceId, tag), subscriber)

  private def removeEventsByPersistenceIdAndTagSubscriber(subscriber: ActorRef): Unit = {
    val keys = eventsByPersistenceIdAndTagSubscribers.collect { case (k, s) if s.contains(subscriber) ⇒ k }
    keys.foreach { key ⇒ eventsByPersistenceIdAndTagSubscribers.removeBinding(key, subscriber) }
  }

  protected def sendEventsByPersistenceIdAndTagSubscriberTerminated(ref: ActorRef): Unit =
    self ! EventsByPersistenceIdTagSubscriberTerminated(ref)

  protected def receiveEventsByPersistenceIdAndTagRegistry: Actor.Receive = {
    case JdbcReadJournal.EventsByPersistenceIdAndTagRequest(persistenceId, tag) ⇒
      addEventsByTagSubscriber(sender(), persistenceId, tag)
      context.watch(sender())

    case EventsByPersistenceIdTagSubscriberTerminated(ref) ⇒
      removeEventsByPersistenceIdAndTagSubscriber(ref)
  }

  private def unwrapTagged(event: Any): Any = event match {
    case Tagged(payload, tags) ⇒ payload
    case _                     ⇒ event
  }

  protected def eventsByPersistenceIdAndTagFlow(atomicWrites: Iterable[AtomicWrite]): Flow[Try[Iterable[SerializationResult]], Try[Iterable[SerializationResult]], NotUsed] =
    Flow[Try[Iterable[SerializationResult]]].map { atomicWriteResult ⇒
      if (hasEventsByTagSubscribers) {
        for {
          seqSerialized ← atomicWriteResult
          serialized ← seqSerialized
          persistenceId = serialized.persistenceId
          tags ← serialized.tags
          tag ← serializationFacade.decodeTags(tags)
          key = subscriberKey(persistenceId, tag)
          if eventsByPersistenceIdAndTagSubscribers contains key
          atomicWrite ← atomicWrites
          if atomicWrite.persistenceId == serialized.persistenceId
          persistentRepr ← atomicWrite.payload
          if persistentRepr.sequenceNr == serialized.sequenceNr
          subscriber ← eventsByPersistenceIdAndTagSubscribers(key)
          envelope = EventEnvelope(persistentRepr.sequenceNr, persistentRepr.persistenceId, persistentRepr.sequenceNr, unwrapTagged(persistentRepr.payload))
          eventAppended = JdbcReadJournal.EventAppended(envelope)
        } subscriber ! eventAppended
      }
      atomicWriteResult
    }
}
