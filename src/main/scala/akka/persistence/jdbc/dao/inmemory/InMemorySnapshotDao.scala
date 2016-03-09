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

import akka.actor.ActorRef
import akka.pattern.ask
import akka.persistence.jdbc.dao.SnapshotDao
import akka.persistence.jdbc.snapshot.SlickSnapshotStore.{ NotSerialized, Serialized, SerializationResult }
import akka.stream.Materializer
import akka.util.Timeout

import scala.concurrent.{ ExecutionContext, Future }

object InMemorySnapshotDao {
  /**
   * Factory method
   */
  def apply(db: ActorRef)(implicit timeout: Timeout, ec: ExecutionContext, mat: Materializer): SnapshotDao =
    new InMemorySnapshotDao(db)
}

class InMemorySnapshotDao(db: ActorRef)(implicit timeout: Timeout, ec: ExecutionContext, mat: Materializer) extends SnapshotDao {
  import InMemorySnapshotStorage._
  override def delete(persistenceId: String, sequenceNr: Long): Future[Unit] =
    (db ? Delete(persistenceId, sequenceNr)).map(_ ⇒ ())

  override def deleteAllSnapshots(persistenceId: String): Future[Unit] =
    (db ? DeleteAllSnapshots(persistenceId)).map(_ ⇒ ())

  override def deleteUpToMaxSequenceNrAndMaxTimestamp(persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long): Future[Unit] =
    (db ? DeleteUpToMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp)).map(_ ⇒ ())

  override def deleteUpToMaxTimestamp(persistenceId: String, maxTimestamp: Long): Future[Unit] =
    (db ? DeleteUpToMaxTimestamp(persistenceId, maxTimestamp)).map(_ ⇒ ())

  override def deleteUpToMaxSequenceNr(persistenceId: String, maxSequenceNr: Long): Future[Unit] =
    (db ? DeleteUpToMaxSequenceNr(persistenceId, maxSequenceNr)).map(_ ⇒ ())

  override def save(persistenceId: String, sequenceNr: Long, timestamp: Long, snapshot: SerializationResult): Future[Unit] =
    (db ? Save(persistenceId, sequenceNr, timestamp, snapshot)).map(_ ⇒ ())

  override def snapshotForMaxSequenceNr(persistenceId: String): Future[Option[SerializationResult]] =
    (db ? SnapshotForMaxSequenceNr(persistenceId, Long.MaxValue)).mapTo[Option[SnapshotData]]
      .map {
        case Some(SnapshotData(_, _, _, ser: Serialized))    ⇒ Some(ser)
        case Some(SnapshotData(_, _, _, ser: NotSerialized)) ⇒ Some(ser)
        case None                                            ⇒ None
      }

  override def snapshotForMaxSequenceNr(persistenceId: String, sequenceNr: Long): Future[Option[SerializationResult]] =
    (db ? SnapshotForMaxSequenceNr(persistenceId, sequenceNr)).mapTo[Option[SnapshotData]]
      .map {
        case Some(SnapshotData(_, _, _, ser: Serialized))    ⇒ Some(ser)
        case Some(SnapshotData(_, _, _, ser: NotSerialized)) ⇒ Some(ser)
        case None                                            ⇒ None
      }

  override def snapshotForMaxTimestamp(persistenceId: String, timestamp: Long): Future[Option[SerializationResult]] =
    (db ? SnapshotForMaxTimestamp(persistenceId, timestamp)).mapTo[Option[SnapshotData]]
      .map {
        case Some(SnapshotData(_, _, _, ser: Serialized))    ⇒ Some(ser)
        case Some(SnapshotData(_, _, _, ser: NotSerialized)) ⇒ Some(ser)
        case None                                            ⇒ None
      }

  override def snapshotForMaxSequenceNrAndMaxTimestamp(persistenceId: String, sequenceNr: Long, timestamp: Long): Future[Option[SerializationResult]] =
    (db ? SnapshotForMaxSequenceNrAndMaxTimestamp(persistenceId, sequenceNr, timestamp)).mapTo[Option[SnapshotData]]
      .map {
        case Some(SnapshotData(_, _, _, ser: Serialized))    ⇒ Some(ser)
        case Some(SnapshotData(_, _, _, ser: NotSerialized)) ⇒ Some(ser)
        case None                                            ⇒ None
      }
}