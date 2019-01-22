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

package akka.persistence.jdbc.snapshot.dao

import akka.persistence.SnapshotMetadata
import akka.persistence.jdbc.config.SnapshotConfig
import akka.persistence.jdbc.snapshot.dao.SnapshotTables.SnapshotRow
import akka.serialization.Serialization
import akka.stream.Materializer
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class ByteArraySnapshotDao(db: JdbcBackend#Database, profile: JdbcProfile, snapshotConfig: SnapshotConfig, serialization: Serialization)(implicit ec: ExecutionContext, val mat: Materializer) extends SnapshotDao {
  import profile.api._

  val queries = new SnapshotQueries(profile, snapshotConfig.snapshotTableConfiguration)

  val serializer = new ByteArraySnapshotSerializer(serialization)

  def toSnapshotData(row: SnapshotRow): (SnapshotMetadata, Any) =
    serializer.deserialize(row) match {
      case Success(deserialized) => deserialized
      case Failure(cause)        => throw cause
    }

  override def latestSnapshot(persistenceId: String): Future[Option[(SnapshotMetadata, Any)]] = for {
    rows <- db.run(queries.selectLatestByPersistenceId(persistenceId).result)
  } yield rows.headOption map toSnapshotData

  override def snapshotForMaxTimestamp(persistenceId: String, maxTimestamp: Long): Future[Option[(SnapshotMetadata, Any)]] = for {
    rows <- db.run(queries.selectOneByPersistenceIdAndMaxTimestamp(persistenceId, maxTimestamp).result)
  } yield rows.headOption map toSnapshotData

  override def snapshotForMaxSequenceNr(persistenceId: String, maxSequenceNr: Long): Future[Option[(SnapshotMetadata, Any)]] = for {
    rows <- db.run(queries.selectOneByPersistenceIdAndMaxSequenceNr(persistenceId, maxSequenceNr).result)
  } yield rows.headOption map toSnapshotData

  override def snapshotForMaxSequenceNrAndMaxTimestamp(persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long): Future[Option[(SnapshotMetadata, Any)]] = for {
    rows <- db.run(queries.selectOneByPersistenceIdAndMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp).result)
  } yield rows.headOption map toSnapshotData

  override def save(snapshotMetadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    val eventualSnapshotRow = Future.fromTry(serializer.serialize(snapshotMetadata, snapshot))
    eventualSnapshotRow.map(queries.insertOrUpdate).flatMap(db.run).map(_ => ())
  }

  override def delete(persistenceId: String, sequenceNr: Long): Future[Unit] = for {
    _ <- db.run(queries.selectByPersistenceIdAndSequenceNr(persistenceId, sequenceNr).delete)
  } yield ()

  override def deleteAllSnapshots(persistenceId: String): Future[Unit] = for {
    _ <- db.run(queries.selectAll(persistenceId).delete)
  } yield ()

  override def deleteUpToMaxSequenceNr(persistenceId: String, maxSequenceNr: Long): Future[Unit] = for {
    _ <- db.run(queries.selectByPersistenceIdUpToMaxSequenceNr(persistenceId, maxSequenceNr).delete)
  } yield ()

  override def deleteUpToMaxTimestamp(persistenceId: String, maxTimestamp: Long): Future[Unit] = for {
    _ <- db.run(queries.selectByPersistenceIdUpToMaxTimestamp(persistenceId, maxTimestamp).delete)
  } yield ()

  override def deleteUpToMaxSequenceNrAndMaxTimestamp(persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long): Future[Unit] = for {
    _ <- db.run(queries.selectByPersistenceIdUpToMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp).delete)
  } yield ()
}
