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

import akka.NotUsed
import akka.actor.ActorSystem
import akka.persistence.jdbc.dao.JournalDao
import akka.persistence.jdbc.dao.varchar.JournalTables.JournalRow
import akka.persistence.jdbc.extension.AkkaPersistenceConfig
import akka.persistence.jdbc.serialization.{ SerializationResult, Serialized }
import akka.serialization.{ Serialization, SerializationExtension, Serializer }
import akka.stream.scaladsl.{ Flow, Source }
import akka.stream.{ ActorMaterializer, Materializer }
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

/**
 * The resulting byte array must be a PersistentRepr, so the serializer must
 * deconstruct a PersistentRepr from a byte array and create a serialization format
 * that contains all of the fields necessary for the deserialization step to reconstruct
 * the PersistentRepr. The serialization format must contain the following fields:
 * <ul>
 * <li>payload: Any</li>
 * <li>sequenceNr: Long</li>
 * <li>persistenceId: String</li>
 * <li>manifest: String</li>
 * <li>deleted: Boolean</li>
 * <li>sender: ActorRef</li>
 * <li>writerUuid: String</li>
 * </ul>
 * Some serialize/deserialize flows:
 * <ul>
 * <li>serialize: (PersistentRepr as Byte Array => SERIALIZATION_FORMAT)</li>
 * <li>deserialize: (SERIALIZATION_FORMAT => PersistentRepr as Byte Array)</li>
 * </ul>
 * for example:
 * <ul>
 * <li>serialize: PersistentRepr as Byte Array => Base64</li>
 * <li>deserialize: Base64 => PersistentRepr as Byte Array</li>
 * </li>
 *
 */
trait VarcharSerialization {
  def serialize(bytes: Array[Byte], serializer: Serializer): Array[Byte] = serializer.toBinary(bytes)

  def serialize(xs: Iterable[SerializationResult], serializer: Serializer): Try[Iterable[SerializationResult]] =
    Try(xs.collect { case e: Serialized ⇒ e }.map(e ⇒ e.copy(serialized = serialize(e.serialized, serializer))))

  def deserialize(message: String, serializer: Serializer): Array[Byte] = serializer.fromBinary(message.getBytes(`UTF-8`)) match {
    case xs: Array[Byte] ⇒ xs
    case other           ⇒ throw new IllegalArgumentException("VarcharSerialization only accepts byte arrays, not [" + other + "]")
  }

  final val `UTF-8` = "UTF-8"

  def toByteArray(message: String): Array[Byte] = message.getBytes(`UTF-8`)
}

/**
 * The DefaultJournalDao contains all the knowledge to persist and load serialized journal entries
 */
class VarcharJournalDao(db: JdbcBackend#Database, val profile: JdbcProfile, system: ActorSystem) extends JournalDao with VarcharSerialization {

  import profile.api._

  implicit val ec: ExecutionContext = system.dispatcher

  implicit val mat: Materializer = ActorMaterializer()(system)

  val serialization: Serialization = SerializationExtension(system)

  val config: AkkaPersistenceConfig = AkkaPersistenceConfig(system)

  def getSerializer: Option[Serializer] = config.serializationConfiguration.serializationIdentity.map(id ⇒ serialization.serializerByIdentity(id))

  val queries: JournalQueries = new JournalQueries(profile, AkkaPersistenceConfig(system).journalTableConfiguration, AkkaPersistenceConfig(system).deletedToTableConfiguration)

  val writeMessages: Flow[Try[Iterable[SerializationResult]], Try[Iterable[SerializationResult]], NotUsed] = Flow[Try[Iterable[SerializationResult]]].mapAsync(1) {
    case element @ Success(xs) ⇒ writeList(xs).map(_ ⇒ element)
    case element @ Failure(t)  ⇒ Future.successful(element)
  }

  override def writeList(xs: Iterable[SerializationResult]): Future[Unit] = for {
    _ ← db.run(queries.writeList(xs))
  } yield ()

  override def writeFlow: Flow[Try[Iterable[SerializationResult]], Try[Iterable[SerializationResult]], NotUsed] =
    Flow[Try[Iterable[SerializationResult]]]
      .map {
        case element @ Success(xs) ⇒ getSerializer.map(serializer ⇒ serialize(xs, serializer)).getOrElse(element)
        case element @ Failure(t)  ⇒ element
      }.via(writeMessages)

  override def countJournal: Future[Int] = for {
    count ← db.run(queries.countJournal.result)
  } yield count

  override def delete(persistenceId: String, maxSequenceNr: Long): Future[Unit] = {
    val actions = (for {
      highestSequenceNr ← queries.highestSequenceNrForPersistenceId(persistenceId).result
      _ ← queries.selectByPersistenceIdAndMaxSequenceNumber(persistenceId, maxSequenceNr).delete
      _ ← queries.insertDeletedTo(persistenceId, highestSequenceNr)
    } yield ()).transactionally
    db.run(actions)
  }

  override def highestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    val actions = for {
      seqNumFoundInJournalTable ← queries.highestSequenceNumberFromJournalForPersistenceIdFromSequenceNr(persistenceId, fromSequenceNr).result
      highestSeqNumberFoundInDeletedToTable ← queries.selectHighestSequenceNrFromDeletedTo(persistenceId).result
      highestSequenceNumber = seqNumFoundInJournalTable.getOrElse(highestSeqNumberFoundInDeletedToTable.getOrElse(0L))
    } yield highestSequenceNumber
    db.run(actions)
  }

  def mapToSerialized(row: JournalRow): Serialized = {
    val deserializedRow: Array[Byte] = getSerializer
      .map(serializer ⇒ deserialize(row.message, serializer))
      .getOrElse(toByteArray(row.message))
    Serialized(row.persistenceId, row.sequenceNumber, deserializedRow, row.tags, row.created)
  }

  override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[SerializationResult, NotUsed] =
    Source.fromPublisher(db.stream(queries.messagesQuery(persistenceId, fromSequenceNr, toSequenceNr, max).result)) map mapToSerialized

  override def persistenceIds(queryListOfPersistenceIds: Iterable[String]): Future[Seq[String]] = for {
    xs ← db.run(queries.journalRowByPersistenceIds(queryListOfPersistenceIds).result)
  } yield xs

  override def allPersistenceIdsSource: Source[String, NotUsed] =
    Source.fromPublisher(db.stream(queries.allPersistenceIdsDistinct.result))

  override def eventsByTag(tag: String, offset: Long): Source[SerializationResult, NotUsed] =
    Source.fromPublisher(db.stream(queries.eventsByTag(tag, offset).result)) map mapToSerialized

  override def eventsByPersistenceIdAndTag(persistenceId: String, tag: String, offset: Long): Source[SerializationResult, NotUsed] =
    Source.fromPublisher(db.stream(queries.eventsByTagAndPersistenceId(persistenceId, tag, offset).result)) map mapToSerialized
}
