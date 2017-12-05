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
import akka.persistence.query.{EventEnvelope, Offset}
import akka.persistence.query.javadsl._
import akka.stream.javadsl.Source

object JdbcReadJournal {
  final val Identifier = ScalaJdbcReadJournal.Identifier
}

class JdbcReadJournal(journal: ScalaJdbcReadJournal) extends ReadJournal
    with CurrentPersistenceIdsQuery
    with PersistenceIdsQuery
    with CurrentEventsByPersistenceIdQuery
    with EventsByPersistenceIdQuery
    with CurrentEventsByTagQuery
    with EventsByTagQuery {

  override def currentPersistenceIds(): Source[String, NotUsed] =
    journal.currentPersistenceIds().asJava

  override def persistenceIds(): Source[String, NotUsed] =
    journal.persistenceIds().asJava

  override def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    journal.currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).asJava

  override def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    journal.eventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).asJava

  /**
    * Same type of query as [[EventsByTagQuery#eventsByTag]] but the event stream
    * is completed immediately when it reaches the end of the "result set". Events that are
    * stored after the query is completed are not included in the event stream.
    *
    * akka-persistence-jdbc has implemented this feature by using a LIKE %tag% query on the tags column.
    * A consequence of this is that tag names must be chosen wisely: for example when querying the tag `User`,
    * events with the tag `UserEmail` will also be returned (since User is a substring of UserEmail).
    *
    * The returned event stream is ordered by `offset`.
    */
  override def currentEventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] =
    journal.currentEventsByTag(tag, offset).asJava

  /**
    * Query events that have a specific tag.
    *
    * akka-persistence-jdbc has implemented this feature by using a LIKE %tag% query on the tags column.
    * A consequence of this is that tag names must be chosen wisely: for example when querying the tag `User`,
    * events with the tag `UserEmail` will also be returned (since User is a substring of UserEmail).
    *
    * The consumer can keep track of its current position in the event stream by storing the
    * `offset` and restart the query from a given `offset` after a crash/restart.
    *
    * For akka-persistence-jdbc the `offset` corresponds to the `ordering` column in the Journal table.
    * The `ordering` is a sequential id number that uniquely identifies the position of each event within
    * the event stream.
    *
    * The returned event stream is ordered by `offset`.
    *
    * The stream is not completed when it reaches the end of the currently stored events,
    * but it continues to push new events when new events are persisted.
    * Corresponding query that is completed when it reaches the end of the currently
    * stored events is provided by [[CurrentEventsByTagQuery#currentEventsByTag]].
    */
  override def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed] =
    journal.eventsByTag(tag, offset).asJava
}
