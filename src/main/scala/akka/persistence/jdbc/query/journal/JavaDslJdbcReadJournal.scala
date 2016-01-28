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

package akka.persistence.jdbc.query.journal

import akka.NotUsed
import akka.persistence.query.EventEnvelope
import akka.persistence.query.javadsl._
import akka.stream.javadsl.Source

object JavaDslJdbcReadJournal {
  final val Identifier = JdbcReadJournal.Identifier
}

class JavaDslJdbcReadJournal(journal: JdbcReadJournal) extends ReadJournal
    with CurrentPersistenceIdsQuery
    with AllPersistenceIdsQuery
    with CurrentEventsByPersistenceIdQuery
    with EventsByPersistenceIdQuery
    with CurrentEventsByTagQuery
    with EventsByTagQuery
    with javadsl.CurrentEventsByPersistenceIdAndTagQuery
    with javadsl.EventsByPersistenceIdAndTagQuery {

  override def currentPersistenceIds(): Source[String, NotUsed] =
    journal.currentPersistenceIds().asJava

  override def allPersistenceIds(): Source[String, NotUsed] =
    journal.allPersistenceIds().asJava

  override def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    journal.currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).asJava

  override def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    journal.eventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).asJava

  override def currentEventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    journal.currentEventsByTag(tag, offset).asJava

  override def eventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    journal.eventsByTag(tag, offset).asJava

  override def currentEventsByPersistenceIdAndTag(persistenceId: String, tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    journal.currentEventsByPersistenceIdAndTag(persistenceId, tag, offset).asJava

  override def eventsByPersistenceIdAndTag(persistenceId: String, tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    journal.eventsByPersistenceIdAndTag(persistenceId, tag, offset).asJava
}
