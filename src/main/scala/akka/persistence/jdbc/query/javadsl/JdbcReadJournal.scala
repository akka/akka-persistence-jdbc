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

package akka.persistence.jdbc.query.javadsl

import akka.NotUsed
import akka.persistence.jdbc.query.scaladsl.{JdbcReadJournal => ScalaJdbcReadJournal}
import akka.persistence.query.{EventEnvelope, Offset, EventEnvelope2, javadsl}
import akka.stream.javadsl.Source

object JdbcReadJournal {
  final val Identifier = ScalaJdbcReadJournal.Identifier
}

class JdbcReadJournal(journal: ScalaJdbcReadJournal) extends javadsl.ReadJournal
    with javadsl.CurrentPersistenceIdsQuery
    with javadsl.AllPersistenceIdsQuery
    with javadsl.CurrentEventsByPersistenceIdQuery
    with javadsl.EventsByPersistenceIdQuery
    with javadsl.CurrentEventsByTagQuery
    with javadsl.CurrentEventsByTagQuery2
    with javadsl.EventsByTagQuery
    with javadsl.EventsByTagQuery2 {

  override def currentPersistenceIds(): Source[String, NotUsed] = {
    journal.currentPersistenceIds().asJava
  }

  override def allPersistenceIds(): Source[String, NotUsed] = {
    journal.allPersistenceIds().asJava
  }

  override def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] = {
    journal.currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).asJava
  }

  override def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] = {
    journal.eventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).asJava
  }

  override def currentEventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] = {
    journal.currentEventsByTag(tag, offset).asJava
  }

  override def currentEventsByTag(tag: String, offset: Offset): Source[EventEnvelope2, NotUsed] = {
    journal.currentEventsByTag(tag, offset).asJava
  }

  override def eventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] = {
    journal.eventsByTag(tag, offset).asJava
  }

  override def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope2, NotUsed] = {
    journal.eventsByTag(tag, offset).asJava
  }
}
