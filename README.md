# akka-persistence-jdbc
Akka-persistence-jdbc is a plugin for akka-persistence that synchronously writes journal and snapshot entries entries to a configured JDBC store. It supports writing journal messages and snapshots to two tables: the `journal` table and the `snapshot` table.

Service | Status | Description
------- | ------ | -----------
License | [![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) | Apache 2.0
Bintray | [![Download](https://api.bintray.com/packages/dnvriend/maven/akka-persistence-jdbc/images/download.svg) ](https://bintray.com/dnvriend/maven/akka-persistence-jdbc/_latestVersion) | Latest Version on Bintray

**Start of Disclaimer:**

> This plugin should not be used in production, ever! For a good, stable and scalable solution use [Apache Cassandra](http://cassandra.apache.org/) with the [akka-persistence-cassandra plugin](https://github.com/akka/akka-persistence-cassandra) Only use this plug-in for study projects and proof of concepts. Please use Docker and [library/cassandra](https://registry.hub.docker.com/u/library/cassandra/) You have been warned! 

**End of Disclaimer**

## New release
The latest version is `v2.0.1` and breaks backwards compatibility in a big way. New features:

- It uses [Typesafe Slick](http://slick.typesafe.com/) as the database backend,
- It uses a new database schema, dropping some columns and changing the column types,
- It writes the journal and snapshot entries as byte arrays,
- It relies on [Akka Serialization](http://doc.akka.io/docs/akka/2.4.1/scala/serialization.html),
- For serializing, please split the domain model from the storage model, and use a binary format for the storage model that support schema versioning like [Google's protocol buffers](https://developers.google.com/protocol-buffers/docs/overview), as it is used by Akka Persistence, and is available as a dependent library. For an example on how to use Akka Serialization with protocol buffers, you can examine the [akka-serialization-test](https://github.com/dnvriend/akka-serialization-test) study project,
- It supports the `Persistence Query` interface thus providing a universal asynchronous stream based query interface,
- It has been tested against MySQL and Postgres only.

## Installation
Add the following to your `build.sbt`:

```scala
resolvers += "dnvriend at bintray" at "http://dl.bintray.com/dnvriend/maven"

libraryDependencies += "com.github.dnvriend" %% "akka-persistence-jdbc" % "2.0.2"
```

## Configuration
The new plugin relies on Slick to do create the SQL dialect for the database in use, therefor the following must be
configured in `application.conf`

Configure `akka-persistence`:
- instruct akka persistence to use the `jdbc-journal` plugin,
- instruct akka persistence to use the `jdbc-snapshot-store` plugin,

Configure `slick`:
- the slick `driver` to use: `slick.driver.PostgresDriver` and `slick.driver.MySQLDriver` are supported,
- the `jdbcDriverClass`, eg: `org.postgresql.ds.PGSimpleDataSource`,
- the `url` which is the JDBC URL,
- the `user` to to use when creating a database connection,
- the `password` to use when creating a database connection,
- Slick uses an `executor` that manages the thread pool for asynchronous execution of Database I/O Actions. The values below are default.
   
```bash
akka {
  persistence {
    journal.plugin = "jdbc-journal"
    snapshot-store.plugin = "jdbc-snapshot-store"
  }
}

akka-persistence-jdbc {
  slick {
    driver = "slick.driver.PostgresDriver"
    jdbcDriverClass = "org.postgresql.ds.PGSimpleDataSource"
    url = "jdbc:postgresql://localhost:5432"/docker"
    user = "docker"
    password = "docker"
    executor {
      name = "slick-executor"
      numThreads = 10
      queueSize = 1000
    }
  }
}
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
import akka.persistence.jdbc.query.journal.JdbcReadJournal

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()(system)
val readJournal: JdbcReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

val willNotCompleteTheStream: Source[String, Unit] = readJournal.allPersistenceIds()

val willCompleteTheStream: Source[String, Unit] = readJournal.currentPersistenceIds()
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
import akka.persistence.query.PersistenceQuery
import akka.persistence.jdbc.query.journal.JdbcReadJournal

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()(system)
val readJournal: JdbcReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

val willCompleteTheStream: Source[String, Unit] = readJournal.currentEventsByPersistenceId("some-persistence-id", 0L, Long.MaxValue)
```

You can retrieve a subset of all events by specifying `fromSequenceNr` and `toSequenceNr` or use `0L` and `Long.MaxValue` respectively to retrieve all events. Note that the corresponding sequence number of each event is provided in the `EventEnvelope`, which makes it possible to resume the stream at a later point from a given sequence number.

The returned event stream is ordered by sequence number, i.e. the same order as the PersistentActor persisted the events. The same prefix of stream elements (in same order) are returned for multiple executions of the query, except for when events have been deleted.

The stream is completed with failure if there is a failure in executing the query in the backend journal.


## Postgres Schema
```sql
DROP TABLE IF EXISTS public.journal;

CREATE TABLE IF NOT EXISTS public.journal (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  message BYTEA NOT NULL,
  PRIMARY KEY(persistence_id, sequence_number)
);

DROP TABLE IF EXISTS public.deleted_to;

CREATE TABLE IF NOT EXISTS public.deleted_to (
  persistence_id VARCHAR(255) NOT NULL,
  deleted_to BIGINT NOT NULL
);

DROP TABLE IF EXISTS public.snapshot;

CREATE TABLE IF NOT EXISTS public.snapshot (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  created BIGINT NOT NULL,
  snapshot BYTEA NOT NULL,
  PRIMARY KEY(persistence_id, sequence_number)
);
```

## MySQL Schema
```sql
DROP TABLE IF EXISTS journal;

CREATE TABLE IF NOT EXISTS journal (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  message BLOB NOT NULL,
  PRIMARY KEY(persistence_id, sequence_number)
);

DROP TABLE IF EXISTS deleted_to;

CREATE TABLE IF NOT EXISTS deleted_to (
  persistence_id VARCHAR(255) NOT NULL,
  deleted_to BIGINT NOT NULL
);

DROP TABLE IF EXISTS snapshot;

CREATE TABLE IF NOT EXISTS snapshot (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  created BIGINT NOT NULL,
  snapshot BLOB NOT NULL,
  PRIMARY KEY (persistence_id, sequence_number)
);
```

# Usage
The user manual has been moved to [the wiki](https://github.com/dnvriend/akka-persistence-jdbc/wiki)

# What's new?
For the full list of what's new see [this wiki page] (https://github.com/dnvriend/akka-persistence-jdbc/wiki/Version-History).

## 2.0.2 (2016-01-17)
 - Support for the `eventsByPersistenceId` live query

## 2.0.1 (2016-01-17)
 - Support for the `allPersistenceIds` live query

## 2.0.0 (2016-01-16)
 - A complete rewrite using [slick](http://slick.typesafe.com/) as the database backend, breaking backwards compatibility in a big way.

## 1.2.2 (2015-10-14) - Akka v2.4.x
 - Merged PR #28 [Andrey Kouznetsov](https://github.com/prettynatty) Removing Unused ExecutionContext, thanks!
 
## 1.1.9 (2015-10-12) - Akka v2.3.x
 - scala 2.10.5 -> 2.10.6
 - akka 2.3.13 -> 2.3.14
 - scalikejdbc 2.2.7 -> 2.2.8
 
# Code of Conduct
**Contributors all agree to follow the [W3C Code of Ethics and Professional Conduct](http://www.w3.org/Consortium/cepc/).**

If you want to take action, feel free to contact Dennis Vriend <dnvriend@gmail.com>. You can also contact W3C Staff as explained in [W3C Procedures](http://www.w3.org/Consortium/pwe/#Procedures).

# License
This source code is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0). The [quick summary of what this license means is available here](https://tldrlegal.com/license/apache-license-2.0-(apache-2.0))

Have fun!
