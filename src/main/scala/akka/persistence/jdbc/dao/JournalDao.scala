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

package akka.persistence.jdbc.dao

import akka.NotUsed
import akka.actor.ActorSystem
import akka.persistence.jdbc.extension.AkkaPersistenceConfig
import akka.persistence.jdbc.serialization.{ SerializationResult, Serialized }
import akka.stream.scaladsl._
import akka.stream.{ ActorMaterializer, Materializer }
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

trait JournalDao {

  /**
   * Returns distinct stream of persistenceIds
   */
  def allPersistenceIdsSource: Source[String, NotUsed]

  /**
   * Returns the number of rows in the journal
   */
  def countJournal: Future[Int]

  /**
   * Deletes all persistent messages up to toSequenceNr (inclusive) for the persistenceId
   */
  def delete(persistenceId: String, toSequenceNr: Long): Future[Unit]

  /**
   * Returns a Source of bytes for certain tag from an offset. The result is sorted by
   * created time asc thus the offset is relative to the creation time
   */
  def eventsByTag(tag: String, offset: Long): Source[SerializationResult, NotUsed]

  /**
   * Returns a Source of bytes for certain persistenceId/tag combination from an offset. The result is sorted by
   * created time asc thus the offset is relative to the creation time
   */
  def eventsByPersistenceIdAndTag(persistenceId: String, tag: String, offset: Long): Source[SerializationResult, NotUsed]

  /**
   * Returns the highest sequence number for the events that are stored for that `persistenceId`. When no events are
   * found for the `persistenceId`, 0L will be the highest sequence number
   */
  def highestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long]

  /**
   * Returns a Source of bytes for a certain persistenceId
   */
  def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[SerializationResult, NotUsed]

  /**
   * Returns the persistenceIds that are available on request of a query list of persistence ids
   */
  def persistenceIds(queryListOfPersistenceIds: Iterable[String]): Future[Seq[String]]

  /**
   * Writes serialized messages
   */
  def writeList(xs: Iterable[SerializationResult]): Future[Unit]

  /**
   * Writes serialized messages
   */
  def writeFlow: Flow[Try[Iterable[SerializationResult]], Try[Iterable[SerializationResult]], NotUsed]
}

/**
 * The DefaultJournalDao contains all the knowledge to persist and load serialized journal entries
 */
class DefaultJournalDao(db: JdbcBackend#Database, val profile: JdbcProfile, system: ActorSystem) extends JournalDao {
  import profile.api._

  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = ActorMaterializer()(system)

  val queries = new DefaultJournalQueries(profile, AkkaPersistenceConfig(system).journalTableConfiguration, AkkaPersistenceConfig(system).deletedToTableConfiguration)

  private def writeMessages: Flow[Try[Iterable[SerializationResult]], Try[Iterable[SerializationResult]], NotUsed] = Flow[Try[Iterable[SerializationResult]]].mapAsync(1) {
    case element @ Success(xs) ⇒ writeList(xs).map(_ ⇒ element)
    case element @ Failure(t)  ⇒ Future.failed(t)
  }

  override def writeList(xs: Iterable[SerializationResult]): Future[Unit] = for {
    _ ← db.run(queries.writeList(xs))
  } yield ()

  override def writeFlow: Flow[Try[Iterable[SerializationResult]], Try[Iterable[SerializationResult]], NotUsed] =
    Flow[Try[Iterable[SerializationResult]]].via(writeMessages)

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
    val actions = (for {
      seqNumFoundInJournalTable ← queries.highestSequenceNumberFromJournalForPersistenceIdFromSequenceNr(persistenceId, fromSequenceNr).result
      highestSeqNumberFoundInDeletedToTable ← queries.selectHighestSequenceNrFromDeletedTo(persistenceId).result
      highestSequenceNumber = seqNumFoundInJournalTable.getOrElse(highestSeqNumberFoundInDeletedToTable.getOrElse(0L))
    } yield highestSequenceNumber).transactionally
    db.run(actions)
  }

  override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[SerializationResult, NotUsed] =
    Source.fromPublisher(db.stream(queries.messagesQuery(persistenceId, fromSequenceNr, toSequenceNr, max).result))
      .map(row ⇒ Serialized(row.persistenceId, row.sequenceNumber, row.message, row.tags, row.created))

  override def persistenceIds(queryListOfPersistenceIds: Iterable[String]): Future[Seq[String]] = for {
    xs ← db.run(queries.journalRowByPersistenceIds(queryListOfPersistenceIds).result)
  } yield xs

  override def allPersistenceIdsSource: Source[String, NotUsed] =
    Source.fromPublisher(db.stream(queries.allPersistenceIdsDistinct.result))

  override def eventsByTag(tag: String, offset: Long): Source[SerializationResult, NotUsed] =
    Source.fromPublisher(db.stream(queries.eventsByTag(tag, offset).result))
      .map(row ⇒ Serialized(row.persistenceId, row.sequenceNumber, row.message, row.tags, row.created))

  override def eventsByPersistenceIdAndTag(persistenceId: String, tag: String, offset: Long): Source[SerializationResult, NotUsed] =
    Source.fromPublisher(db.stream(queries.eventsByTagAndPersistenceId(persistenceId, tag, offset).result))
      .map(row ⇒ Serialized(row.persistenceId, row.sequenceNumber, row.message, row.tags, row.created))
}