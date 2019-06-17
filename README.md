# akka-persistence-jdbc

[![Join the chat at https://gitter.im/dnvriend/akka-persistence-jdbc](https://badges.gitter.im/dnvriend/akka-persistence-jdbc.svg)](https://gitter.im/dnvriend/akka-persistence-jdbc?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Join the contributors chat at https://gitter.im/akka-persistence-jdbc/contributors](https://img.shields.io/badge/chat-contributors%20channel-green.svg)](https://gitter.im/akka-persistence-jdbc/contributors?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/dnvriend/akka-persistence-jdbc.svg?branch=master)](https://travis-ci.org/dnvriend/akka-persistence-jdbc)
[![Download](https://api.bintray.com/packages/dnvriend/maven/akka-persistence-jdbc/images/download.svg)](https://bintray.com/dnvriend/maven/akka-persistence-jdbc/_latestVersion)
[![Latest version](https://index.scala-lang.org/dnvriend/akka-persistence-jdbc/akka-persistence-jdbc/latest.svg)](https://index.scala-lang.org/dnvriend/akka-persistence-jdbc/akka-persistence-jdbc)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/a5d8576c2a56479ab1c40d87c78bba58)](https://www.codacy.com/app/dnvriend/akka-persistence-jdbc?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=dnvriend/akka-persistence-jdbc&amp;utm_campaign=Badge_Grade)
[![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

akka-persistence-jdbc writes journal and snapshot entries to a configured JDBC store. It implements the full akka-persistence-query API and is therefore very useful for implementing DDD-style application models using Akka and Scala for creating reactive applications.

## Installation
Add the following to your `build.sbt`:

```scala
libraryDependencies += "com.github.dnvriend" %% "akka-persistence-jdbc" % "3.5.2"
```

## Release notes

The release notes can be found [here](https://github.com/dnvriend/akka-persistence-jdbc/releases).

For change log prior to v3.2.0, visit [Version History Page (wiki)](https://github.com/dnvriend/akka-persistence-jdbc/wiki/Version-History).

## Contribution policy

Contributions via GitHub pull requests are gladly accepted from their original author. Along with any pull requests, please state that the contribution is your original work and that you license the work to the project under the project's open source license. Whether or not you state this explicitly, by submitting any copyrighted material via pull request, email, or other means you agree to license the material under the project's open source license and warrant that you have the legal authority to do so.

## Code of Conduct
Contributors all agree to follow the [W3C Code of Ethics and Professional Conduct][w3c-cond].

If you want to take action, feel free to contact Dennis Vriend <dnvriend@gmail.com>. You can also contact W3C Staff as explained in [W3C Procedures][w3c-proc].

## License
This source code is made available under the [Apache 2.0 License][apache]. The [quick summary of what this license means is available here](https://tldrlegal.com/license/apache-license-2.0-(apache-2.0))

## Configuration
The plugin relies on Slick to do create the SQL dialect for the database in use, therefore the following must be configured in `application.conf`

Configure `akka-persistence`:
- instruct akka persistence to use the `jdbc-journal` plugin,
- instruct akka persistence to use the `jdbc-snapshot-store` plugin,

Configure `slick`:
- The following slick profiles are supported:
  - `slick.jdbc.PostgresProfile$`
  - `slick.jdbc.MySQLProfile$`
  - `slick.jdbc.H2Profile$`
  - `slick.jdbc.OracleProfile$`
  - `slick.jdbc.SQLServerProfile$`

## Database Schema

- [Postgres Schema](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/schema/postgres/postgres-schema.sql)
- [MySQL Schema](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/schema/mysql/mysql-schema.sql)
- [H2 Schema](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/schema/h2/h2-schema.sql)
- [Oracle Schema](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/schema/oracle/oracle-schema.sql)
- [SQL Server Schema](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/schema/sqlserver/sqlserver-schema.sql)

## Configuration

akka-persistence-jdbc provides the defaults as part of the [reference.conf](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/main/resources/reference.conf)
this file documents all the values which can be configured.

There are several possible ways to configure loading your database connections. Options will be explained below.

### One database connection pool per journal type
 
There is the possibility to create a separate database connection pool per journal-type (one pool for the write-journal,
one pool for the snapshot-journal, and one pool for the read-journal). This is the default and the following example
configuration shows how this is configured:

- [Postgres](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/postgres-application.conf)
- [MySQL](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/mysql-application.conf)
- [H2](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/h2-application.conf)
- [Oracle](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/oracle-application.conf)
- [SQL Server](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/sqlserver-application.conf)

### Sharing the database connection pool between the journals

In order to create only one connection pool which is shared between all journals the following configuration can be used:

- [Postgres](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/postgres-shared-db-application.conf)
- [MySQL](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/mysql-shared-db-application.conf)
- [H2](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/h2-shared-db-application.conf)
- [Oracle](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/oracle-shared-db-application.conf)
- [SQL Server](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/sqlserver-shared-db-application.conf)

### Customized loading of the db connection

It is also possible to load a custom database connection. 
In order to do so a custom implementation of [SlickDatabaseProvider](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/main/scala/akka/persistence/jdbc/util/SlickExtension.scala)
needs to be created. The methods that need to be implemented supply the Slick `Database` and `Profile` to the journals.

To enable your custom `SlickDatabaseProvider`, the fully qualified class name of the `SlickDatabaseProvider`
needs to be configured in the application.conf. In addition, you might want to consider whether you want
the database to be closed automatically:

```
akka-persistence-jdbc {
  database-provider-fqcn = "com.mypackage.CustomSlickDatabaseProvider"
}
jdbc-journal {
  use-shared-db = "enabled" // setting this to any non-empty string prevents the journal from closing the database on shutdown
}
jdbc-snapshot-store {
  use-shared-db = "enabled" // setting this to any non-empty string prevents the snapshot-journal from closing the database on shutdown
}
```


### DataSource lookup by JNDI name
The plugin uses `Slick` as the database access library. Slick [supports jndi][slick-jndi] for looking up [DataSource][ds]s.

To enable the JNDI lookup, you must add the following to your application.conf:

```
jdbc-journal {
  slick {
    profile = "slick.jdbc.PostgresProfile$"
    jndiName = "java:jboss/datasources/PostgresDS"   
  }
}
```

When using the `use-shared-db = slick` setting, the follow configuration can serve as an example:

```
akka-persistence-jdbc {
  shared-databases {
    slick {
      profile = "slick.jdbc.PostgresProfile$"
      jndiName = "java:/jboss/datasources/bla"
    }
  }
}
```

## How to get the ReadJournal using Scala
The `ReadJournal` is retrieved via the `akka.persistence.query.PersistenceQuery` extension:

```scala
import akka.persistence.query.PersistenceQuery
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal

val readJournal: JdbcReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)
```

## How to get the ReadJournal using Java
The `ReadJournal` is retrieved via the `akka.persistence.query.PersistenceQuery` extension:

```java
import akka.persistence.query.PersistenceQuery
import akka.persistence.jdbc.query.javadsl.JdbcReadJournal

final JdbcReadJournal readJournal = PersistenceQuery.get(system).getReadJournalFor(JdbcReadJournal.class, JdbcReadJournal.Identifier());
```

## Persistence Query
The plugin supports the following queries:

## AllPersistenceIdsQuery and CurrentPersistenceIdsQuery
`allPersistenceIds` and `currentPersistenceIds` are used for retrieving all persistenceIds of all persistent actors.

```scala
import akka.actor.ActorSystem
import akka.stream.{Materializer, ActorMaterializer}
import akka.stream.scaladsl.Source
import akka.persistence.query.PersistenceQuery
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()(system)
val readJournal: JdbcReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

val willNotCompleteTheStream: Source[String, NotUsed] = readJournal.allPersistenceIds()

val willCompleteTheStream: Source[String, NotUsed] = readJournal.currentPersistenceIds()
```

The returned event stream is unordered and you can expect different order for multiple executions of the query.

When using the `allPersistenceIds` query, the stream is not completed when it reaches the end of the currently used persistenceIds,
but it continues to push new persistenceIds when new persistent actors are created.

When using the `currentPersistenceIds` query, the stream is completed when the end of the current list of persistenceIds is reached,
thus it is not a `live` query.

The stream is completed with failure if there is a failure in executing the query in the backend journal.

## EventsByPersistenceIdQuery and CurrentEventsByPersistenceIdQuery
`eventsByPersistenceId` and `currentEventsByPersistenceId` is used for retrieving events for
a specific PersistentActor identified by persistenceId.

```scala
import akka.actor.ActorSystem
import akka.stream.{Materializer, ActorMaterializer}
import akka.stream.scaladsl.Source
import akka.persistence.query.{ PersistenceQuery, EventEnvelope }
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()(system)
val readJournal: JdbcReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

val willNotCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.eventsByPersistenceId("some-persistence-id", 0L, Long.MaxValue)

val willCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.currentEventsByPersistenceId("some-persistence-id", 0L, Long.MaxValue)
```

You can retrieve a subset of all events by specifying `fromSequenceNr` and `toSequenceNr` or use `0L` and `Long.MaxValue` respectively to retrieve all events. Note that the corresponding sequence number of each event is provided in the `EventEnvelope`, which makes it possible to resume the stream at a later point from a given sequence number.

The returned event stream is ordered by sequence number, i.e. the same order as the PersistentActor persisted the events. The same prefix of stream elements (in same order) are returned for multiple executions of the query, except for when events have been deleted.

The stream is completed with failure if there is a failure in executing the query in the backend journal.

## EventsByTag and CurrentEventsByTag
`eventsByTag` and `currentEventsByTag` are used for retrieving events that were marked with a given
`tag`, e.g. all domain events of an Aggregate Root type.

```scala
import akka.actor.ActorSystem
import akka.stream.{Materializer, ActorMaterializer}
import akka.stream.scaladsl.Source
import akka.persistence.query.{ PersistenceQuery, EventEnvelope }
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()(system)
val readJournal: JdbcReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

val willNotCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.eventsByTag("apple", 0L)

val willCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.currentEventsByTag("apple", 0L)
```

## Tagging events
To tag events you'll need to create an [Event Adapter][event-adapter] that will wrap the event in a [akka.persistence.journal.Tagged](http://doc.akka.io/api/akka/2.4.1/#akka.persistence.journal.Tagged) class with the given tags. The `Tagged` class will instruct `akka-persistence-jdbc` to tag the event with the given set of tags.

The persistence plugin will __not__ store the `Tagged` class in the journal. It will strip the `tags` and `payload` from the `Tagged` class, and use the class only as an instruction to tag the event with the given tags and store the `payload` in the  `message` field of the journal table.

```scala
import akka.persistence.journal.{ Tagged, WriteEventAdapter }
import com.github.dnvriend.Person.{ LastNameChanged, FirstNameChanged, PersonCreated }

class TaggingEventAdapter extends WriteEventAdapter {
  override def manifest(event: Any): String = ""

  def withTag(event: Any, tag: String) = Tagged(event, Set(tag))

  override def toJournal(event: Any): Any = event match {
    case _: PersonCreated =>
      withTag(event, "person-created")
    case _: FirstNameChanged =>
      withTag(event, "first-name-changed")
    case _: LastNameChanged =>
      withTag(event, "last-name-changed")
    case _ => event
  }
}
```

The `EventAdapter` must be registered by adding the following to the root of `application.conf` Please see the  [demo-akka-persistence-jdbc](https://github.com/dnvriend/demo-akka-persistence-jdbc) project for more information.

```bash
jdbc-journal {
  event-adapters {
    tagging = "com.github.dnvriend.TaggingEventAdapter"
  }
  event-adapter-bindings {
    "com.github.dnvriend.Person$PersonCreated" = tagging
    "com.github.dnvriend.Person$FirstNameChanged" = tagging
    "com.github.dnvriend.Person$LastNameChanged" = tagging
  }
}
```

You can retrieve a subset of all events by specifying offset, or use 0L to retrieve all events with a given tag. The offset corresponds to an ordered sequence number for the specific tag. Note that the corresponding offset of each  event is provided in the EventEnvelope, which makes it possible to resume the stream at a later point from a given offset.

In addition to the offset the EventEnvelope also provides persistenceId and sequenceNr for each event. The sequenceNr is  the sequence number for the persistent actor with the persistenceId that persisted the event. The persistenceId + sequenceNr  is an unique identifier for the event.

The returned event stream contains only events that correspond to the given tag, and is ordered by the creation time of the events. The same stream elements (in same order) are returned for multiple executions of the same query. Deleted events are not deleted from the tagged event stream.

## Custom DAO Implementation
The plugin supports loading a custom DAO for the journal and snapshot. You should implement a custom Data Access Object (DAO) if you wish to alter the default persistency strategy in
any way, but wish to reuse all the logic that the plugin already has in place, eg. the Akka Persistence Query API. For example, the default persistency strategy that the plugin
supports serializes journal and snapshot messages using a serializer of your choice and stores them as byte arrays in the database.

By means of configuration in `application.conf` a DAO can be configured, below the default DAOs are shown:

```bash
jdbc-journal {
  dao = "akka.persistence.jdbc.journal.dao.ByteArrayJournalDao"
}

jdbc-snapshot-store {
  dao = "akka.persistence.jdbc.snapshot.dao.ByteArraySnapshotDao"
}

jdbc-read-journal {
  dao = "akka.persistence.jdbc.query.dao.ByteArrayReadJournalDao"
}
```

Storing messages as byte arrays in blobs is not the only way to store information in a database. For example, you could store messages with full type information as a normal database rows, each event type having its own table.
For example, implementing a Journal Log table that stores all persistenceId, sequenceNumber and event type discriminator field, and storing the event data in another table with full typing

You only have to implement two interfaces `akka.persistence.jdbc.journal.dao.JournalDao` and/or `akka.persistence.jdbc.snapshot.dao.SnapshotDao`. As these APIs are only now exposed for public use, the interfaces may change when the API needs to
change for whatever reason.

For example, take a look at the following two custom DAOs:

```scala
class MyCustomJournalDao(db: Database, val profile: JdbcProfile, journalConfig: JournalConfig, serialization: Serialization)(implicit ec: ExecutionContext, mat: Materializer) extends JournalDao {
    // snip
}

class MyCustomSnapshotDao(db: JdbcBackend#Database, val profile: JdbcProfile, snapshotConfig: SnapshotConfig, serialization: Serialization)(implicit ec: ExecutionContext, val mat: Materializer) extends SnapshotDao {
    // snip
}
```

As you can see, the custom DAOs get a _Slick database_, a _Slick profile_, the journal or snapshot _configuration_, an _akka.serialization.Serialization_, an _ExecutionContext_ and _Materializer_ injected after constructed.
You should register the Fully Qualified Class Name in `application.conf` so that the custom DAOs will be used.

For more information please review the two default implementations `akka.persistence.jdbc.dao.bytea.journal.ByteArrayJournalDao` and `akka.persistence.jdbc.dao.bytea.snapshot.ByteArraySnapshotDao` or the demo custom DAO example from the [demo-akka-persistence](https://github.com/dnvriend/demo-akka-persistence-jdbc) site.

## Explicitly shutting down the database connections
The plugin automatically shuts down the HikariCP connection pool when the ActorSystem is terminated.
This is done using [ActorSystem.registerOnTermination](https://doc.akka.io/api/akka/current/akka/actor/ActorSystem.html#registerOnTermination[T](code:=>T):Unit).

[slick]: http://slick.lightbend.com/
[slick-jndi]: http://slick.typesafe.com/doc/3.3.0/database.html#using-a-jndi-name
[apache]: http://www.apache.org/licenses/LICENSE-2.0
[w3c-cond]: http://www.w3.org/Consortium/cepc/
[w3c-proc]: http://www.w3.org/Consortium/pwe/#Procedures
[ds]: http://docs.oracle.com/javase/8/docs/api/javax/sql/DataSource.html
[event-adapter]: http://doc.akka.io/docs/akka/current/scala/persistence.html#event-adapters-scala
