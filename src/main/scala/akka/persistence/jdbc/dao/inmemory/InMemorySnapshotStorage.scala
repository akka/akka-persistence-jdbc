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
import akka.persistence.jdbc.snapshot.SlickSnapshotStore.SerializationResult

object InMemorySnapshotStorage {
  case class SnapshotData(persistenceId: String, sequenceNumber: Long, created: Long, snapshot: SerializationResult)

  // Success
  case class Delete(persistenceId: String, sequenceNr: Long)

  // Success
  case class DeleteAllSnapshots(persistenceId: String)

  // Success
  case class DeleteUpToMaxSequenceNr(persistenceId: String, maxSequenceNr: Long)

  // Success
  case class DeleteUpToMaxTimestamp(persistenceId: String, maxTimestamp: Long)

  // Success
  case class DeleteUpToMaxSequenceNrAndMaxTimestamp(persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long)

  // Success
  case object Clear

  // Success
  case class Save(persistenceId: String, sequenceNr: Long, timestamp: Long, snapshot: SerializationResult)

  // [Option[SerializationResult]]
  case class SnapshotForMaxSequenceNr(persistenceId: String, sequenceNr: Long)

  // [Option[SerializationResult]]
  case class SnapshotForMaxSequenceNrAndMaxTimestamp(persistenceId: String, sequenceNr: Long, timestamp: Long)

  // [Option[SerializationResult]]
  case class SnapshotForMaxTimestamp(persistenceId: String, timestamp: Long)
}

class InMemorySnapshotStorage extends Actor with ActorLogging {
  import InMemorySnapshotStorage._

  var snapshot = Map.empty[String, Vector[SnapshotData]]

  def clear(ref: ActorRef): Unit = {
    log.debug("[Clear]")
    ref ! Success("")
  }

  def delete(ref: ActorRef, persistenceId: String, sequenceNr: Long): Unit = {
    log.debug(s"s[delete]: pid: $persistenceId, seqno: $sequenceNr, snapshotStore: ${snapshot.get(persistenceId).map(_.sortBy(_.sequenceNumber).map(s ⇒ s"${s.persistenceId} - ${s.sequenceNumber}"))}")
    for {
      xs ← snapshot.get(persistenceId)
      x ← xs
      if x.sequenceNumber == sequenceNr
    } {
      val key = persistenceId
      snapshot += (key -> snapshot.getOrElse(key, Vector.empty).filterNot(_.sequenceNumber == sequenceNr))
      log.debug(s"[deleting]: ${x.persistenceId} - ${x.sequenceNumber}, rest: ${snapshot.get(persistenceId).map(_.sortBy(_.sequenceNumber).map(s ⇒ s"${s.persistenceId} - ${s.sequenceNumber}"))}")
    }
    log.debug(s"s[delete-finished]: pid: $persistenceId, seqno: $sequenceNr, snapshotStore: ${snapshot.get(persistenceId).map(_.sortBy(_.sequenceNumber).map(s ⇒ s"${s.persistenceId} - ${s.sequenceNumber}"))}")
    ref ! Success("")
  }

  def deleteUpToMaxSequenceNr(ref: ActorRef, persistenceId: String, maxSequenceNr: Long): Unit = {
    for {
      xs ← snapshot.get(persistenceId)
      x ← xs
      if x.sequenceNumber <= maxSequenceNr
    } {
      log.debug(s"[deleting]: $x, rest: ${snapshot.get(persistenceId)}")
      val key = persistenceId
      snapshot += (key -> snapshot.getOrElse(key, Vector.empty).filterNot(_.sequenceNumber == x.sequenceNumber))
    }

    log.debug(s"[deleteUpToMaxSequenceNr]: pid: $persistenceId, maxSeqNo: $maxSequenceNr")
    ref ! Success("")
  }

  def deleteAllSnapshots(ref: ActorRef, persistenceId: String): Unit = {
    snapshot -= persistenceId
    log.debug(s"[deleteAllSnapshots]: $persistenceId")
    ref ! Success("")
  }

  def deleteUpToMaxTimestamp(ref: ActorRef, persistenceId: String, maxTimestamp: Long): Unit = {
    for {
      xs ← snapshot.get(persistenceId)
      x ← xs
      if x.created <= maxTimestamp
    } {
      log.debug(s"[deleting]: $x, rest: ${snapshot.get(persistenceId)}")
      val key = persistenceId
      snapshot += (key -> snapshot.getOrElse(key, Vector.empty).filterNot(_.sequenceNumber == x.sequenceNumber))
    }

    log.debug(s"[deleteUpToMaxTimestamp]: pid: $persistenceId, maxTimestamp: $maxTimestamp")
    ref ! Success("")
  }

  def deleteUpToMaxSequenceNrAndMaxTimestamp(ref: ActorRef, persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long): Unit = {
    for {
      xs ← snapshot.get(persistenceId)
      x ← xs
      if x.sequenceNumber <= maxSequenceNr && x.created <= maxTimestamp
    } {
      val key = persistenceId
      snapshot += (key -> snapshot.getOrElse(key, Vector.empty).filterNot(_.sequenceNumber == x.sequenceNumber))
      log.debug(s"[deleting]: $x rest: ${snapshot.get(persistenceId)}")
    }
    log.debug(s"[deleteUpToMaxSequenceNrAndMaxTimestamp]: pid: $persistenceId, maxSeqNo: $maxSequenceNr, maxTimestamp: $maxTimestamp")
    ref ! Success("")
  }

  def save(ref: ActorRef, persistenceId: String, sequenceNr: Long, timestamp: Long, ser: SerializationResult): Unit = {
    val key = persistenceId
    snapshot += (key -> (snapshot.getOrElse(key, Vector.empty) :+ SnapshotData(persistenceId, sequenceNr, timestamp, ser)))
    log.debug(s"[Save]: Saving snapshot: pid: $persistenceId, seqnr: $sequenceNr, timestamp: $timestamp")
    ref ! Success("")
  }

  def snapshotForMaxSequenceNr(ref: ActorRef, persistenceId: String, sequenceNr: Long): Unit = {
    val determine = snapshot.get(persistenceId).flatMap(xs ⇒ xs.filter(_.sequenceNumber <= sequenceNr).toList.sortBy(_.sequenceNumber).reverse.headOption)
    log.debug(s"[snapshotForMaxSequenceNr]: pid: $persistenceId, seqno: $sequenceNr, returning: $determine")
    ref ! determine
  }

  def snapshotFor(ref: ActorRef, persistenceId: String)(p: SnapshotData ⇒ Boolean): Unit = {
    val determine: Option[SnapshotData] = snapshot.get(persistenceId).flatMap(_.find(p))
    log.debug(s"[snapshotFor]: pid: $persistenceId: returning: $determine")
    ref ! determine
  }

  def snapshotForMaxSequenceNrAndMaxTimestamp(ref: ActorRef, persistenceId: String, sequenceNr: Long, timestamp: Long): Unit = {
    log.debug(s"[snapshotForMaxSequenceNrAndMaxTimestamp]: pid: $persistenceId, seqno: $sequenceNr, timestamp: $timestamp")
    snapshotFor(ref, persistenceId)(snap ⇒ snap.sequenceNumber == sequenceNr)
  }

  def snapshotForMaxTimestamp(ref: ActorRef, persistenceId: String, timestamp: Long): Unit =
    snapshotFor(ref, persistenceId)(_.created < timestamp)

  override def receive: Receive = {
    case Delete(persistenceId: String, sequenceNr: Long)                                                        ⇒ delete(sender(), persistenceId, sequenceNr)
    case DeleteAllSnapshots(persistenceId: String)                                                              ⇒ deleteAllSnapshots(sender(), persistenceId)
    case DeleteUpToMaxTimestamp(persistenceId: String, maxTimestamp: Long)                                      ⇒ deleteUpToMaxTimestamp(sender(), persistenceId, maxTimestamp)
    case DeleteUpToMaxSequenceNr(persistenceId: String, maxSequenceNr: Long)                                    ⇒ deleteUpToMaxSequenceNr(sender(), persistenceId, maxSequenceNr)
    case DeleteUpToMaxSequenceNrAndMaxTimestamp(persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long) ⇒ deleteUpToMaxSequenceNrAndMaxTimestamp(sender(), persistenceId, maxSequenceNr, maxTimestamp)
    case Clear                                                                                                  ⇒ clear(sender())
    case Save(persistenceId: String, sequenceNr: Long, timestamp: Long, snapshot: SerializationResult)          ⇒ save(sender(), persistenceId, sequenceNr, timestamp, snapshot)
    case SnapshotForMaxSequenceNr(persistenceId: String, sequenceNr: Long)                                      ⇒ snapshotForMaxSequenceNr(sender(), persistenceId, sequenceNr)
    case SnapshotForMaxSequenceNrAndMaxTimestamp(persistenceId: String, sequenceNr: Long, timestamp: Long)      ⇒ snapshotForMaxSequenceNrAndMaxTimestamp(sender(), persistenceId, sequenceNr, timestamp)
    case SnapshotForMaxTimestamp(persistenceId: String, timestamp: Long)                                        ⇒ snapshotForMaxTimestamp(sender(), persistenceId, timestamp)
  }
}
