package akka.persistence.jdbc;

import akka.Done;
import akka.actor.ActorSystem;
// #create
import akka.persistence.jdbc.testkit.javadsl.SchemaUtils;
// #create
// #read-journal
import akka.persistence.query.PersistenceQuery;
import akka.persistence.jdbc.query.javadsl.JdbcReadJournal;
// #read-journal

import java.util.concurrent.CompletionStage;

class JavadslSnippets {
    void create() {
        // #create

        ActorSystem actorSystem = ActorSystem.create("example");
        CompletionStage<Done> done = SchemaUtils.createIfNotExists(actorSystem);
        // #create
    }

    void readJournal() {
        ActorSystem system = ActorSystem.create("example");
        // #read-journal

        final JdbcReadJournal readJournal = PersistenceQuery.get(system).getReadJournalFor(JdbcReadJournal.class, JdbcReadJournal.Identifier());
        // #read-journal

    }
}
