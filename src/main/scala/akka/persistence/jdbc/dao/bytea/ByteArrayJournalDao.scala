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

import akka.NotUsed
import akka.persistence.AtomicWrite
import akka.persistence.jdbc.config.JournalConfig
import akka.persistence.jdbc.dao.JournalDao
import akka.persistence.jdbc.dao.bytea.JournalTables.JournalRow
import akka.persistence.jdbc.serialization.{SerializationResult, Serialized}
import akka.serialization.Serialization
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * The DefaultJournalDao contains all the knowledge to persist and load serialized journal entries
 */
class ByteArrayJournalDao(db: Database, val profile: JdbcProfile, journalConfig: JournalConfig, serialization: Serialization)(implicit ec: ExecutionContext, mat: Materializer) extends JournalDao {
  import profile.api._

  val queries = new JournalQueries(profile, journalConfig.journalTableConfiguration, journalConfig.deletedToTableConfiguration)

  val serializer = new ByteArrayJournalSerializer(serialization, journalConfig.pluginConfig.tagSeparator)

  private def futureExtractor: Flow[Try[Future[Unit]], Try[Unit], NotUsed] =
    Flow[Try[Future[Unit]]].mapAsync(1) {
      case Success(future) => future.map(Success(_))
      case Failure(t) => Future.successful(Failure(t))
    }

  private def writeJournalRows(xs: Iterable[JournalRow]): Future[Unit] = for {
    _ ← db.run(queries.writeJournalRows(xs))
  } yield ()

  override def writeFlow: Flow[AtomicWrite, Try[Unit], NotUsed] =
    Flow[AtomicWrite]
    .via(serializer.serializeFlow)
    .map(_.map(writeJournalRows))
    .via(futureExtractor)

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

  override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[SerializationResult, NotUsed] =
    Source.fromPublisher(db.stream(queries.messagesQuery(persistenceId, fromSequenceNr, toSequenceNr, max).result))
      .map(row ⇒ Serialized(row.persistenceId, row.sequenceNumber, row.message, row.tags, row.created))
}
