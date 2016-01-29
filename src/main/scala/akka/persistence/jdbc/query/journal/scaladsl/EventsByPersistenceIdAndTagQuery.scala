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

package akka.persistence.jdbc.query.journal.scaladsl

import akka.NotUsed
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl.ReadJournal
import akka.stream.scaladsl.Source

trait EventsByPersistenceIdAndTagQuery extends ReadJournal {
  /**
   * Query events that have a specific persistenceId/tag combination
   * This is an optimization query as the database can filter out the
   * events for a specific persistenceId
   */
  def eventsByPersistenceIdAndTag(persistenceId: String, tag: String, offset: Long): Source[EventEnvelope, NotUsed]
}
