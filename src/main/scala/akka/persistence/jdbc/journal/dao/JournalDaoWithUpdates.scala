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

import akka.Done

import scala.concurrent.Future

/**
 * A [[JournalDao]] with extended capabilities, such as updating payloads and tags of existing events.
 * These operations should be used sparingly, for example for migrating data from un-encrypted to encrypted formats
 */
trait JournalDaoWithUpdates extends JournalDao {

  /**
   * Update (!) an existing event with the passed in data.
   */
  def update(persistenceId: String, sequenceNr: Long, payload: AnyRef): Future[Done]
}
