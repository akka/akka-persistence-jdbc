/*
 * Copyright 2015 Dennis Vriend
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

package akka.persistence.jdbc.journal

import akka.persistence._
import akka.persistence.jdbc.common.ActorConfig
import akka.persistence.journal.AsyncWriteJournal

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.util.Try

trait JdbcSyncWriteJournal extends AsyncWriteJournal with ActorConfig with JdbcStatements {
  override def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] =
    if (messages.nonEmpty)
      Future.fromTry(Try(writeMessages(messages)))
    else
      Future.successful(Seq.empty)

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] =
    Future.fromTry(Try(deleteMessagesTo(persistenceId, toSequenceNr)))

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] =
    Future.fromTry(Try(readHighestSequenceNr(persistenceId)))

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) ⇒ Unit): Future[Unit] =
    Future.fromTry(Try(replayMessages(persistenceId, fromSequenceNr, toSequenceNr, max).foreach(replayCallback)))
}
