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
import akka.persistence.jdbc.dao.{ FlowGraphWriteMessagesFacade, JournalDao, WriteMessagesFacade }
import akka.persistence.jdbc.serialization.Serialized
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.Timeout

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

object InMemoryJournalDao {
  /**
   * Factory method
   */
  def apply(db: ActorRef)(implicit timeout: Timeout, ec: ExecutionContext, mat: Materializer): JournalDao = new InMemoryJournalDao(db)
}

class InMemoryJournalDao(db: ActorRef)(implicit timeout: Timeout, ec: ExecutionContext, mat: Materializer) extends JournalDao {

  import InMemoryJournalStorage._

  val writeMessagesFacade: WriteMessagesFacade = new FlowGraphWriteMessagesFacade(this)

  override def allPersistenceIdsSource: Source[String, NotUsed] =
    Source.fromFuture((db ? AllPersistenceIds).mapTo[Set[String]])
      .mapConcat(identity)

  override def writeFlow: Flow[Try[Iterable[Serialized]], Try[Iterable[Serialized]], NotUsed] =
    Flow[Try[Iterable[Serialized]]].via(writeMessagesFacade.writeMessages)

  override def eventsByPersistenceIdAndTag(persistenceId: String, tag: String, offset: Long): Source[Array[Byte], NotUsed] =
    Source.fromFuture((db ? EventsByPersistenceIdAndTag(persistenceId, tag, offset)).mapTo[List[Serialized]])
      .map(_.map(_.serialized))
      .mapConcat(identity)

  override def countJournal: Future[Int] = (db ? CountJournal).mapTo[Int]

  override def highestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] =
    (db ? HighestSequenceNr(persistenceId, fromSequenceNr)).mapTo[Long]

  override def eventsByTag(tag: String, offset: Long): Source[Array[Byte], NotUsed] =
    Source.fromFuture((db ? EventsByTag(tag, offset)).mapTo[List[Serialized]])
      .map(_.map(_.serialized))
      .mapConcat(identity)

  override def persistenceIds(queryListOfPersistenceIds: Iterable[String]): Future[Seq[String]] =
    (db ? PersistenceIds(queryListOfPersistenceIds)).mapTo[Seq[String]]

  override def writeList(xs: Iterable[Serialized]): Future[Unit] = {
    (db ? WriteList(xs)).map(_ ⇒ ())
  }

  override def delete(persistenceId: String, toSequenceNr: Long): Future[Unit] =
    (db ? Delete(persistenceId, toSequenceNr)).map(_ ⇒ ())

  override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[Array[Byte], NotUsed] = {
    Source.fromFuture((db ? Messages(persistenceId, fromSequenceNr, toSequenceNr, max)).mapTo[List[Serialized]])
      .map(_.map(_.serialized))
      .mapConcat(identity)
  }
}
