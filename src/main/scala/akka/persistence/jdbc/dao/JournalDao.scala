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
import akka.persistence.jdbc.serialization.SerializationResult
import akka.stream.scaladsl._

import scala.concurrent.Future
import scala.util.Try

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