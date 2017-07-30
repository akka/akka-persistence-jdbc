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

package akka.persistence.jdbc
package query.dao

import akka.NotUsed
import akka.persistence.PersistentRepr
import akka.stream.scaladsl.Source

import scala.collection.immutable._
import scala.concurrent.Future
import scala.util.Try

trait ReadJournalDao {
  /**
   * Returns distinct stream of persistenceIds
   */
  def allPersistenceIdsSource(max: Long): Source[String, NotUsed]

  /**
   * Returns a Source of bytes for certain tag from an offset. The result is sorted by
   * created time asc thus the offset is relative to the creation time
   */
  def eventsByTag(tag: String, offset: Long, maxOffset: Long, max: Long): Source[Try[(PersistentRepr, Set[String], JournalRow)], NotUsed]

  /**
   * Returns a Source of bytes for a certain persistenceId
   */
  def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[Try[PersistentRepr], NotUsed]

  /**
   * @param offset Minimum value to retrieve
   * @param limit Maximum number of values to retrieve
   * @return A Source of journal event sequence numbers (corresponding to the Ordering column)
   */
  def journalSequence(offset: Long, limit: Long): Source[Long, NotUsed]

  /**
   * @return The value of the maximum (ordering) id in the journal
   */
  def maxJournalSequence(): Future[Long]
}