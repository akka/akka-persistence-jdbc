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

import akka.NotUsed
import akka.actor.ActorRef
import akka.pattern.ask
import akka.persistence.jdbc.dao.JournalDao
import akka.persistence.jdbc.serialization.{ SerializationResult, Serialized }
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.Timeout

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

object InMemoryJournalDao {
  /**
   * Factory method
   */
  def apply(db: ActorRef)(implicit timeout: Timeout, ec: ExecutionContext, mat: Materializer): JournalDao = new InMemoryJournalDao(db)
}

class InMemoryJournalDao(db: ActorRef)(implicit timeout: Timeout, ec: ExecutionContext, mat: Materializer) extends JournalDao {

  import InMemoryJournalStorage._

  private def writeMessages: Flow[Try[Iterable[SerializationResult]], Try[Iterable[SerializationResult]], NotUsed] = Flow[Try[Iterable[SerializationResult]]].mapAsync(1) {
    case element @ Success(xs) ⇒ writeList(xs).map(_ ⇒ element)
    case element @ Failure(t)  ⇒ Future.successful(element)
  }

  override def allPersistenceIdsSource: Source[String, NotUsed] =
    Source.fromFuture((db ? AllPersistenceIds).mapTo[Set[String]])
      .mapConcat(identity)

  override def writeFlow: Flow[Try[Iterable[SerializationResult]], Try[Iterable[SerializationResult]], NotUsed] =
    Flow[Try[Iterable[SerializationResult]]].via(writeMessages)

  override def eventsByPersistenceIdAndTag(persistenceId: String, tag: String, offset: Long): Source[SerializationResult, NotUsed] =
    Source.fromFuture((db ? EventsByPersistenceIdAndTag(persistenceId, tag, offset)).mapTo[List[Serialized]])
      .mapConcat(identity)

  override def countJournal: Future[Int] = (db ? CountJournal).mapTo[Int]

  override def highestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] =
    (db ? HighestSequenceNr(persistenceId, fromSequenceNr)).mapTo[Long]

  override def eventsByTag(tag: String, offset: Long): Source[SerializationResult, NotUsed] =
    Source.fromFuture((db ? EventsByTag(tag, offset)).mapTo[List[Serialized]])
      .mapConcat(identity)

  override def persistenceIds(queryListOfPersistenceIds: Iterable[String]): Future[Seq[String]] =
    (db ? PersistenceIds(queryListOfPersistenceIds)).mapTo[Seq[String]]

  override def writeList(xs: Iterable[SerializationResult]): Future[Unit] = {
    (db ? WriteList(xs)).map(_ ⇒ ())
  }

  override def delete(persistenceId: String, toSequenceNr: Long): Future[Unit] =
    (db ? Delete(persistenceId, toSequenceNr)).map(_ ⇒ ())

  override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[SerializationResult, NotUsed] = {
    Source.fromFuture((db ? Messages(persistenceId, fromSequenceNr, toSequenceNr, max)).mapTo[List[SerializationResult]])
      .mapConcat(identity)
  }
}
