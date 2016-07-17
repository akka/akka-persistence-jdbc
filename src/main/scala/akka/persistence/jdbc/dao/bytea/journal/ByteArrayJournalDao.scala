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

package akka.persistence.jdbc.dao.bytea.journal

import akka.NotUsed
import akka.persistence.jdbc.config.JournalConfig
import akka.persistence.jdbc.dao.JournalDao
import akka.persistence.jdbc.dao.bytea.journal.JournalTables.JournalRow
import akka.persistence.jdbc.serialization.FlowPersistentReprSerializer
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.serialization.Serialization
import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Source }
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

/**
 * The DefaultJournalDao contains all the knowledge to persist and load serialized journal entries
 */
trait BaseByteArrayJournalDao extends JournalDao {

  val db: Database
  val profile: JdbcProfile
  val queries: JournalQueries
  val serializer: FlowPersistentReprSerializer[JournalRow]
  implicit val ec: ExecutionContext

  import profile.api._

  private def futureExtractor: Flow[Try[Future[Unit]], Try[Unit], NotUsed] =
    Flow[Try[Future[Unit]]].mapAsync(1) {
      case Success(future) => future.map(Success(_))
      case Failure(t)      => Future.successful(Failure(t))
    }

  private def writeJournalRows(xs: Seq[JournalRow]): Future[Unit] = for {
    _ <- db.run(queries.writeJournalRows(xs))
  } yield ()

  override def writeFlow: Flow[AtomicWrite, Try[Unit], NotUsed] =
    Flow[AtomicWrite]
      .via(serializer.serializeFlow)
      .map(_.map(writeJournalRows))
      .via(futureExtractor)

  override def delete(persistenceId: String, maxSequenceNr: Long): Future[Unit] =
    db.run(queries.markJournalMessagesAsDeleted(persistenceId, maxSequenceNr)).map(_ => ())

  override def highestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] =
    db.run(queries.highestSequenceNrForPersistenceId(persistenceId).result).map(_.getOrElse(0L))

  override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[Try[PersistentRepr], NotUsed] =
    Source.fromPublisher(db.stream(queries.messagesQuery(persistenceId, fromSequenceNr, toSequenceNr, max).result))
      .via(serializer.deserializeFlowWithoutTags)
}

trait H2JournalDao extends JournalDao {
  val profile: JdbcProfile

  private lazy val isH2Driver = profile match {
    case slick.driver.H2Driver => true
    case _                     => false
  }

  abstract override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[Try[PersistentRepr], NotUsed] = {
    super.messages(persistenceId, fromSequenceNr, toSequenceNr, correctMaxForH2Driver(max))
  }

  private def correctMaxForH2Driver(max: Long): Long = {
    if (isH2Driver) {
      Math.min(max, Int.MaxValue) // H2 only accepts a LIMIT clause as an Integer
    } else {
      max
    }
  }
}

class ByteArrayJournalDao(val db: Database, val profile: JdbcProfile, journalConfig: JournalConfig, serialization: Serialization)(implicit val ec: ExecutionContext, val mat: Materializer) extends BaseByteArrayJournalDao with H2JournalDao {
  val queries = new JournalQueries(profile, journalConfig.journalTableConfiguration)
  val serializer = new ByteArrayJournalSerializer(serialization, journalConfig.pluginConfig.tagSeparator)
}

