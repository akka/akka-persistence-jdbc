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

package akka.persistence.jdbc.dao.bytea

import akka.NotUsed
import akka.persistence.jdbc.config.ReadJournalConfig
import akka.persistence.jdbc.dao.ReadJournalDao
import akka.persistence.jdbc.serialization.{ SerializationResult, Serialized }
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend._

import scala.concurrent.ExecutionContext

class ByteArrayReadJournalDao(db: Database, val profile: JdbcProfile, readJournalConfig: ReadJournalConfig)(implicit ec: ExecutionContext, mat: Materializer) extends ReadJournalDao {
  import profile.api._
  val queries = new ReadJournalQueries(profile, readJournalConfig.journalTableConfiguration)

  override def allPersistenceIdsSource: Source[String, NotUsed] =
    Source.fromPublisher(db.stream(queries.allPersistenceIdsDistinct.result))

  override def eventsByTag(tag: String, offset: Long): Source[SerializationResult, NotUsed] =
    Source.fromPublisher(db.stream(queries.eventsByTag(s"%$tag%", offset).result))
      .map(row ⇒ Serialized(row.persistenceId, row.sequenceNumber, row.message, row.tags, row.created))

  override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[SerializationResult, NotUsed] =
    Source.fromPublisher(db.stream(queries.messagesQuery(persistenceId, fromSequenceNr, toSequenceNr, max).result))
      .map(row ⇒ Serialized(row.persistenceId, row.sequenceNumber, row.message, row.tags, row.created))
}
