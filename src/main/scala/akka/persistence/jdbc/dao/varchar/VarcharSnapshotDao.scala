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

package akka.persistence.jdbc.dao.varchar

import akka.actor.ActorSystem
import akka.persistence.SnapshotMetadata
import akka.persistence.jdbc.dao.SnapshotDao
import akka.persistence.jdbc.dao.varchar.SnapshotTables._
import akka.persistence.jdbc.extension.AkkaPersistenceConfig
import akka.persistence.jdbc.snapshot.SlickSnapshotStore.{ SerializationResult, Serialized }
import akka.serialization.{ Serialization, SerializationExtension, Serializer }
import akka.stream.{ ActorMaterializer, Materializer }
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.concurrent.{ ExecutionContext, Future }

class VarcharSnapshotDao(db: JdbcBackend#Database, val profile: JdbcProfile, system: ActorSystem) extends SnapshotDao with VarcharSerialization {
  import profile.api._

  implicit val ec: ExecutionContext = system.dispatcher

  implicit val mat: Materializer = ActorMaterializer()(system)

  val serialization: Serialization = SerializationExtension(system)

  val config: AkkaPersistenceConfig = AkkaPersistenceConfig(system)

  def getSerializer: Option[Serializer] = config.serializationConfiguration.serializationIdentity.map(id ⇒ serialization.serializerByIdentity(id))

  val queries = new SnapshotQueries(profile, AkkaPersistenceConfig(system).snapshotTableConfiguration)

  def mapToSnapshotData(row: SnapshotRow): SerializationResult = {
    val deserializedRow: Array[Byte] = getSerializer
      .map(serializer ⇒ deserialize(row.snapshot, serializer))
      .getOrElse(toByteArray(row.snapshot))
    Serialized(SnapshotMetadata(row.persistenceId, row.sequenceNumber, row.created), deserializedRow)
  }

  override def snapshotForMaxSequenceNr(persistenceId: String): Future[Option[SerializationResult]] = for {
    snapshot ← db.run(queries.selectByPersistenceIdAndMaxSeqNr(persistenceId).result.headOption)
  } yield snapshot map mapToSnapshotData

  override def snapshotForMaxTimestamp(persistenceId: String, maxTimestamp: Long): Future[Option[SerializationResult]] = for {
    snapshot ← db.run(queries.selectByPersistenceIdAndMaxTimestamp(persistenceId, maxTimestamp).result.headOption)
  } yield snapshot map mapToSnapshotData

  override def snapshotForMaxSequenceNr(persistenceId: String, maxSequenceNr: Long): Future[Option[SerializationResult]] = for {
    snapshot ← db.run(queries.selectByPersistenceIdAndMaxSequenceNr(persistenceId, maxSequenceNr).result.headOption)
  } yield snapshot map mapToSnapshotData

  override def snapshotForMaxSequenceNrAndMaxTimestamp(persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long): Future[Option[SerializationResult]] = for {
    snapshot ← db.run(queries.selectByPersistenceIdAndMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp).result.headOption)
  } yield snapshot map mapToSnapshotData

  override def save(persistenceId: String, sequenceNr: Long, created: Long, serializationResult: SerializationResult): Future[Unit] =
    serializationResult match {
      case Serialized(_, snapshot) ⇒
        val serializedByteArray = getSerializer.map(serializer ⇒ serialize(snapshot, serializer)).getOrElse(snapshot)
        db.run(queries.insertOrUpdate(persistenceId, sequenceNr, created, serializedByteArray)).map(_ ⇒ ())
      case _ ⇒ Future.failed(new IllegalArgumentException("The default snapshot dao can only save serialized messages"))
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
