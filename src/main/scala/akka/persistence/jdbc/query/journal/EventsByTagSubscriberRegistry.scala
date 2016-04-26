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

object EventsByTagSubscriberRegistry {
  case class EventsByTagSubscriberTerminated(ref: ActorRef)
}

trait EventsByTagSubscriberRegistry { _: SlickAsyncWriteJournal ⇒
  import EventsByTagSubscriberRegistry._
  private val eventsByTagSubscribers = new mutable.HashMap[String, mutable.Set[ActorRef]] with mutable.MultiMap[String, ActorRef]

  private def hasEventsByTagSubscribers: Boolean = eventsByTagSubscribers.nonEmpty

  private def addEventsByTagSubscriber(subscriber: ActorRef, tag: String): Unit =
    eventsByTagSubscribers.addBinding(tag, subscriber)

  private def removeEventsByTagSubscriber(subscriber: ActorRef): Unit = {
    val keys = eventsByTagSubscribers.collect { case (k, s) if s.contains(subscriber) ⇒ k }
    keys.foreach { key ⇒ eventsByTagSubscribers.removeBinding(key, subscriber) }
  }

  protected def sendEventsByTagSubscriberTerminated(ref: ActorRef): Unit =
    self ! EventsByTagSubscriberTerminated(ref)

  protected def receiveEventsByTagRegistry: Actor.Receive = {
    case JdbcReadJournal.EventsByTagRequest(tag) ⇒
      addEventsByTagSubscriber(sender(), tag)
      context.watch(sender())

    case EventsByTagSubscriberTerminated(ref) ⇒
      removeEventsByTagSubscriber(ref)
  }

  private def unwrapTagged(event: Any): Any = event match {
    case Tagged(payload, tags) ⇒ payload
    case _                     ⇒ event
  }

  protected def eventsByTagFlow(atomicWrites: Iterable[AtomicWrite]): Flow[Try[Iterable[SerializationResult]], Try[Iterable[SerializationResult]], NotUsed] =
    Flow[Try[Iterable[SerializationResult]]].map { atomicWriteResult ⇒
      if (hasEventsByTagSubscribers) {
        for {
          seqSerialized ← atomicWriteResult
          serialized ← seqSerialized
          tags ← serialized.tags
          tag ← serializationFacade.decodeTags(tags)
          if eventsByTagSubscribers contains tag
          atomicWrite ← atomicWrites
          if atomicWrite.persistenceId == serialized.persistenceId
          persistentRepr ← atomicWrite.payload
          if persistentRepr.sequenceNr == serialized.sequenceNr
          subscriber ← eventsByTagSubscribers(tag)
          envelope = EventEnvelope(persistentRepr.sequenceNr, persistentRepr.persistenceId, persistentRepr.sequenceNr, unwrapTagged(persistentRepr.payload))
          eventAppended = JdbcReadJournal.EventAppended(envelope)
        } subscriber ! eventAppended
      }
      atomicWriteResult
    }
}
