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

package akka.persistence.jdbc.dao.inmemory

import akka.actor.Status.Success
import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.event.LoggingReceive
import akka.persistence.jdbc.serialization.{ SerializationFacade, Serialized }

import scala.util.Try

object InMemoryJournalStorage {

  // List[String]
  case object AllPersistenceIds

  // Int
  case object CountJournal

  // List[Array[Byte]]
  case class EventsByPersistenceIdAndTag(persistenceId: String, tag: String, offset: Long)

  // Long
  case class HighestSequenceNr(persistenceId: String, fromSequenceNr: Long)

  // List[Array[Byte]]
  case class EventsByTag(tag: String, offset: Long)

  // List[String]
  case class PersistenceIds(queryListOfPersistenceIds: Iterable[String])

  // Success
  case class WriteList(xs: Iterable[Serialized])

  // Success
  case class Delete(persistenceId: String, toSequenceNr: Long)

  // List[Array[Byte]]
  case class Messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)

  // Success
  case object Clear
}

class InMemoryJournalStorage extends Actor with ActorLogging {
  import InMemoryJournalStorage._

  var journal = Map.empty[String, Vector[Serialized]]

  var deleted_to = Map.empty[String, Vector[Long]]

  def allPersistenceIds(ref: ActorRef): Unit = {
    val determine = journal.keySet
    log.debug(s"[allPersistenceIds]: $determine")
    ref ! determine
  }

  def countJournal(ref: ActorRef): Unit = {
    val determine: Int = journal.values.foldLeft(0) { case (c, s) ⇒ c + s.size }
    log.debug(s"[countJournal]: $determine")
    ref ! determine
  }

  def eventsByPersistenceIdAndTag(ref: ActorRef, persistenceId: String, tag: String, offset: Long): Unit = {
    val determine: Option[List[Serialized]] = for {
      xs ← journal.get(persistenceId)
    } yield (for {
      x ← xs
      if x.tags.exists(tags ⇒ SerializationFacade.decodeTags(tags, ",") contains tag)
    } yield x).toList.sortBy(_.sequenceNr)
    log.debug(s"[eventsByPersistenceIdAndTag]: $determine")
    ref ! determine.getOrElse(Nil)
  }

  def highestSequenceNr(ref: ActorRef, persistenceId: String, fromSequenceNr: Long): Unit = {
    val determineJournal = Try(for {
      xs ← journal.get(persistenceId)
    } yield (for {
      x ← xs
      if x.sequenceNr >= fromSequenceNr
    } yield x.sequenceNr).max(Ordering.Long)).toOption.flatten

    val determineDeletedTo: Option[Long] =
      Try(deleted_to.get(persistenceId).map(_.max(Ordering.Long))).toOption.flatten

    val highest = determineJournal.getOrElse(determineDeletedTo.getOrElse(0L))
    log.debug(s"[highestSequenceNr]: Sending $highest Journal: $determineJournal, deletedTo: $determineDeletedTo deleted_to: $deleted_to")
    ref ! highest
  }

  def eventsByTag(ref: ActorRef, tag: String, offset: Long): Unit = {
    log.debug(s"[eventsByTag] tag: $tag, offset: $offset, journal: ${journal.mapValues(_.map(s ⇒ s"${s.persistenceId} - ${s.sequenceNr} - ${s.tags}"))}")
    val determine: List[Serialized] = (for {
      xs ← journal.values
      x ← xs
      if x.tags.exists(tags ⇒ SerializationFacade.decodeTags(tags, ",") contains tag)
    } yield x).toList
    log.debug(s"[eventsByTag]: sending: $determine")
    ref ! determine
  }

  /**
   * Returns the persistenceIds that are available on request of a query list of persistence ids
   */
  def persistenceIds(ref: ActorRef, queryListOfPersistenceIds: Iterable[String]): Unit = {
    val determine: List[String] = (for {
      pid ← journal.keySet
      if queryListOfPersistenceIds.toList contains pid
    } yield pid).toList

    log.debug(s"[persistenceIds]: $determine")
    ref ! determine
  }

  def writelist(ref: ActorRef, xs: Iterable[Serialized]): Unit = {
    xs.foreach { (serialized: Serialized) ⇒
      val key = serialized.persistenceId
      journal += (key -> (journal.getOrElse(key, Vector.empty[Serialized]) :+ serialized))
      log.debug(s"[writelist]: Adding $serialized, ${journal.mapValues(_.sortBy(_.sequenceNr).map(s ⇒ s"${s.persistenceId}:${s.sequenceNr} - ${s.tags}"))},\ndeleted_to: $deleted_to")
    }
    ref ! Success("")
  }

  def delete(ref: ActorRef, persistenceId: String, toSequenceNr: Long): Unit = {
    for {
      xs ← journal.get(persistenceId)
      x ← xs
      if x.sequenceNr <= toSequenceNr
    } {
      val key = persistenceId
      journal += (key -> journal.getOrElse(key, Vector.empty).filterNot(_.sequenceNr == x.sequenceNr))
      deleted_to += (key -> (deleted_to.getOrElse(key, Vector.empty) :+ x.sequenceNr))
      log.debug(s"[delete]: $x,\njournal: ${journal.mapValues(_.map(s ⇒ s"${s.persistenceId} - ${s.sequenceNr}"))},\ndeleted_to: $deleted_to")
    }
    ref ! Success("")
  }

  def messages(ref: ActorRef, persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Unit = {
    def toTake = if (max >= Int.MaxValue) Int.MaxValue else max.toInt
    val determine: Option[List[Serialized]] = for {
      xs ← journal.get(persistenceId)
    } yield (for {
      x ← xs
      if x.sequenceNr >= fromSequenceNr && x.sequenceNr <= toSequenceNr
    } yield x).toList.sortBy(_.sequenceNr).take(toTake)
    log.debug(s"[messages]: for pid: $persistenceId, from: $fromSequenceNr, to: $toSequenceNr, max: $max => ${determine.map(_.map(s ⇒ s"${s.persistenceId} - ${s.sequenceNr}"))}")
    ref ! determine.getOrElse(Nil)
  }

  def clear(ref: ActorRef): Unit = {
    journal = Map.empty[String, Vector[Serialized]]
    deleted_to = Map.empty[String, Vector[Long]]
    ref ! Success("")
  }

  override def receive: Receive = LoggingReceive {
    case AllPersistenceIds                                          ⇒ allPersistenceIds(sender())
    case CountJournal                                               ⇒ countJournal(sender())
    case EventsByPersistenceIdAndTag(persistenceId, tag, offset)    ⇒ eventsByPersistenceIdAndTag(sender(), persistenceId, tag, offset)
    case HighestSequenceNr(persistenceId, fromSequenceNr)           ⇒ highestSequenceNr(sender(), persistenceId, fromSequenceNr)
    case EventsByTag(tag, offset)                                   ⇒ eventsByTag(sender(), tag, offset)
    case PersistenceIds(queryListOfPersistenceIds)                  ⇒ persistenceIds(sender(), queryListOfPersistenceIds)
    case WriteList(xs)                                              ⇒ writelist(sender(), xs)
    case Delete(persistenceId, toSequenceNr)                        ⇒ delete(sender(), persistenceId, toSequenceNr)
    case Messages(persistenceId, fromSequenceNr, toSequenceNr, max) ⇒ messages(sender(), persistenceId, fromSequenceNr, toSequenceNr, max)
    case Clear                                                      ⇒ clear(sender())
    case msg                                                        ⇒ println("--> Dropping msg: " + msg.getClass.getName)
  }
}
