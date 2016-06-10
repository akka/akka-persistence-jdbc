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

package akka.persistence.jdbc.dao.bytea

import akka.persistence.SnapshotMetadata
import akka.persistence.jdbc.config.SnapshotConfig
import akka.persistence.jdbc.dao.SnapshotDao
import akka.persistence.jdbc.dao.bytea.SnapshotTables.SnapshotRow
import akka.persistence.jdbc.snapshot.JdbcSnapshotStore.{ SerializationResult, Serialized }
import akka.stream.Materializer
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.concurrent.{ ExecutionContext, Future }

class ByteArraySnapshotDao(db: JdbcBackend#Database, val profile: JdbcProfile, snapshotConfig: SnapshotConfig)(implicit ec: ExecutionContext, val mat: Materializer) extends SnapshotDao {
  import profile.api._

  val queries = new SnapshotQueries(profile, snapshotConfig.snapshotTableConfiguration)

  def mapToSnapshotData(row: SnapshotRow): SerializationResult =
    Serialized(SnapshotMetadata(row.persistenceId, row.sequenceNumber, row.created), row.snapshot)

  override def snapshotForMaxSequenceNr(persistenceId: String): Future[Option[SerializationResult]] = for {
    rows ← db.run(queries.selectByPersistenceIdAndMaxSeqNr(persistenceId).result)
  } yield rows.headOption map mapToSnapshotData

  override def snapshotForMaxTimestamp(persistenceId: String, maxTimestamp: Long): Future[Option[SerializationResult]] = for {
    rows ← db.run(queries.selectByPersistenceIdAndMaxTimestamp(persistenceId, maxTimestamp).result)
  } yield rows.headOption map mapToSnapshotData

  override def snapshotForMaxSequenceNr(persistenceId: String, maxSequenceNr: Long): Future[Option[SerializationResult]] = for {
    rows ← db.run(queries.selectByPersistenceIdAndMaxSequenceNr(persistenceId, maxSequenceNr).result)
  } yield rows.headOption map mapToSnapshotData

  override def snapshotForMaxSequenceNrAndMaxTimestamp(persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long): Future[Option[SerializationResult]] = for {
    rows ← db.run(queries.selectByPersistenceIdAndMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp).result)
  } yield rows.headOption map mapToSnapshotData

  override def save(persistenceId: String, sequenceNr: Long, created: Long, serializationResult: SerializationResult): Future[Unit] =
    serializationResult match {
      case Serialized(_, snapshot) ⇒ db.run(queries.insertOrUpdate(persistenceId, sequenceNr, created, snapshot)).map(_ ⇒ ())
      case _                       ⇒ Future.failed(new IllegalArgumentException("The default snapshot dao can only save serialized messages"))
    }

  override def delete(persistenceId: String, sequenceNr: Long): Future[Unit] = for {
    _ ← db.run(queries.selectByPersistenceIdAndSeqNr(persistenceId, sequenceNr).delete)
  } yield ()

  override def deleteAllSnapshots(persistenceId: String): Future[Unit] = for {
    _ ← db.run(queries.selectAll(persistenceId).delete)
  } yield ()

  override def deleteUpToMaxSequenceNr(persistenceId: String, maxSequenceNr: Long): Future[Unit] = for {
    _ ← db.run(queries.selectByPersistenceIdAndMaxSequenceNr(persistenceId, maxSequenceNr).delete)
  } yield ()

  override def deleteUpToMaxTimestamp(persistenceId: String, maxTimestamp: Long): Future[Unit] = for {
    _ ← db.run(queries.selectByPersistenceIdAndMaxTimestamp(persistenceId, maxTimestamp).delete)
  } yield ()

  override def deleteUpToMaxSequenceNrAndMaxTimestamp(persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long): Future[Unit] = for {
    _ ← db.run(queries.selectByPersistenceIdAndMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp).delete)
  } yield ()
}
