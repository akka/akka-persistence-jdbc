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

package akka.persistence.jdbc.journal.dao

import akka.NotUsed
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.stream.scaladsl._

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.util.Try

trait JournalDao {
  /**
   * Deletes all persistent messages up to toSequenceNr (inclusive) for the persistenceId
   */
  def delete(persistenceId: String, toSequenceNr: Long): Future[Unit]

  /**
   * Returns the highest sequence number for the events that are stored for that `persistenceId`. When no events are
   * found for the `persistenceId`, 0L will be the highest sequence number
   */
  def highestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long]

  /**
   * Returns a Source of PersistentRepr for a certain persistenceId
   */
  def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[Try[PersistentRepr], NotUsed]

  /**
   * @see [[akka.persistence.journal.AsyncWriteJournal.asyncWriteMessages(messages)]]
   */
  def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]]
}