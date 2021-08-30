/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
// #create
import akka.persistence.jdbc.testkit.javadsl.SchemaUtils;
// #create
// #read-journal
import akka.persistence.query.*;
import akka.persistence.jdbc.query.javadsl.JdbcReadJournal;
// #read-journal
// #persistence-ids
import akka.stream.javadsl.Source;
import akka.persistence.query.PersistenceQuery;
import akka.persistence.jdbc.query.javadsl.JdbcReadJournal;
// #persistence-ids
// #events-by-persistence-id
import akka.stream.javadsl.Source;
import akka.persistence.query.PersistenceQuery;
import akka.persistence.query.EventEnvelope;
import akka.persistence.jdbc.query.javadsl.JdbcReadJournal;
// #events-by-persistence-id
// #events-by-tag
import akka.stream.javadsl.Source;
import akka.persistence.query.PersistenceQuery;
import akka.persistence.query.EventEnvelope;
import akka.persistence.jdbc.query.javadsl.JdbcReadJournal;
// #events-by-tag

import java.util.concurrent.CompletionStage;

final class JavadslSnippets {
  void create() {
    // #create

    ActorSystem actorSystem = ActorSystem.create("example");
    CompletionStage<Done> done = SchemaUtils.createIfNotExists(actorSystem);
    // #create
  }

  void readJournal() {
    ActorSystem system = ActorSystem.create("example");
    // #read-journal

    final JdbcReadJournal readJournal =
        PersistenceQuery.get(system)
            .getReadJournalFor(JdbcReadJournal.class, JdbcReadJournal.Identifier());
    // #read-journal

  }

  void persistenceIds() {
    ActorSystem system = ActorSystem.create();
    // #persistence-ids

    JdbcReadJournal readJournal =
        PersistenceQuery.get(system)
            .getReadJournalFor(JdbcReadJournal.class, JdbcReadJournal.Identifier());

    Source<String, NotUsed> willNotCompleteTheStream = readJournal.persistenceIds();

    Source<String, NotUsed> willCompleteTheStream = readJournal.currentPersistenceIds();
    // #persistence-ids
  }

  void eventsByPersistenceIds() {
    ActorSystem system = ActorSystem.create();

    // #events-by-persistence-id

    JdbcReadJournal readJournal =
        PersistenceQuery.get(system)
            .getReadJournalFor(JdbcReadJournal.class, JdbcReadJournal.Identifier());

    Source<EventEnvelope, NotUsed> willNotCompleteTheStream =
        readJournal.eventsByPersistenceId("some-persistence-id", 0L, Long.MAX_VALUE);

    Source<EventEnvelope, NotUsed> willCompleteTheStream =
        readJournal.currentEventsByPersistenceId("some-persistence-id", 0L, Long.MAX_VALUE);
    // #events-by-persistence-id
  }

  void eventsByTag() {
    ActorSystem system = ActorSystem.create();
    // #events-by-tag

    JdbcReadJournal readJournal =
        PersistenceQuery.get(system)
            .getReadJournalFor(JdbcReadJournal.class, JdbcReadJournal.Identifier());

    Source<EventEnvelope, NotUsed> willNotCompleteTheStream =
        readJournal.eventsByTag("apple", Offset.sequence(0L));

    Source<EventEnvelope, NotUsed> willCompleteTheStream =
        readJournal.currentEventsByTag("apple", Offset.sequence(0L));
    // #events-by-tag
  }
}
