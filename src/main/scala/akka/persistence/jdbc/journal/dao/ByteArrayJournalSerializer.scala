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
package journal.dao

import akka.persistence.PersistentRepr
import akka.persistence.jdbc.serialization.FlowPersistentReprSerializer
import akka.serialization.Serialization

import scala.collection.immutable._
import scala.util.Try

class ByteArrayJournalSerializer(serialization: Serialization, separator: String)
    extends FlowPersistentReprSerializer[JournalRow] {
  override def serialize(persistentRepr: PersistentRepr, tags: Set[String]): Try[JournalRow] = {
    serialization
      .serialize(persistentRepr)
      .map(
        JournalRow(
          Long.MinValue,
          persistentRepr.deleted,
          persistentRepr.persistenceId,
          persistentRepr.sequenceNr,
          _,
          encodeTags(tags, separator)))
  }

  override def deserialize(journalRow: JournalRow): Try[(PersistentRepr, Set[String], Long)] = {
    serialization
      .deserialize(journalRow.message, classOf[PersistentRepr])
      .map((_, decodeTags(journalRow.tags, separator), journalRow.ordering))
  }
}
