# akka-persistence-jdbc
Akka-persistence-jdbc is a plugin for akka-persistence that synchronously writes journal and snapshot entries entries to a configured JDBC store. It supports writing journal messages and snapshots to two tables: the `journal` table and the `snapshot` table.

Service | Status | Description
------- | ------ | -----------
License | [![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) | Apache 2.0
Bintray | [![Download](https://api.bintray.com/packages/dnvriend/maven/akka-persistence-jdbc/images/download.svg) ](https://bintray.com/dnvriend/maven/akka-persistence-jdbc/_latestVersion) | Latest Version on Bintray

## Commercial notice
Please note that the `akka persistence JDBC plugin` uses the Commercial [Typesafe Slick Extensions](http://slick.typesafe.com/doc/3.1.1/extensions.html).
When you use the `akka persistence plugin` together with either the `Oracle`, `IBM DB2` or `Microsoft SQL Server` database, for use
other than for development and testing purposes, then you need a commercial Typesafe subscription under the terms and conditions of the 
[Typesafe Subscription Agreement (PDF)](http://typesafe.com/public/legal/TypesafeSubscriptionAgreement.pdf). 
A subscription is required for production use, please see http://typesafe.com/how/subscription for details. 

Please note that most of my projects run on [Postgresql](http://www.postgresql.org/), which is the most advanced open source
database available, with some great features, and it works great together with the JDBC plugin.

## New release
The latest version is `v2.1.0` and breaks backwards compatibility with `v1.x.x` in a big way. New features:

- It uses [Typesafe Slick](http://slick.typesafe.com/) as the database backend,
  - Using the typesafe config for the Slick database configuration,
  - Uses HikariCP for the connection pool,
  - It has been tested against Postgres, MySQL and Oracle only.
  - It uses a new database schema, dropping some columns and changing the column types,
  - It writes the journal and snapshot entries as byte arrays,
- It relies on [Akka Serialization](http://doc.akka.io/docs/akka/2.4.1/scala/serialization.html),
  - For serializing, please split the domain model from the storage model, and use a binary format for the storage model that support schema versioning like [Google's protocol buffers](https://developers.google.com/protocol-buffers/docs/overview), as it is used by Akka Persistence, and is available as a dependent library. For an example on how to use Akka Serialization with protocol buffers, you can examine the [akka-serialization-test](https://github.com/dnvriend/akka-serialization-test) study project,
- It supports the `Persistence Query` interface thus providing a universal asynchronous stream based query interface,
- Table names and schema names are configurable,

## Installation
Add the following to your `build.sbt`:

```scala
resolvers += "dnvriend at bintray" at "http://dl.bintray.com/dnvriend/maven"

libraryDependencies += "com.github.dnvriend" %% "akka-persistence-jdbc" % "2.1.0"
```

## Configuration
The new plugin relies on Slick to do create the SQL dialect for the database in use, therefor the following must be
configured in `application.conf`

Configure `akka-persistence`:
- instruct akka persistence to use the `jdbc-journal` plugin,
- instruct akka persistence to use the `jdbc-snapshot-store` plugin,

Configure `slick`:
- The following slick drivers are supported:
  - `slick.driver.PostgresDriver`
  - `slick.driver.MySQLDriver`
  - `com.typesafe.slick.driver.oracle.OracleDriver`
   
# Postgres configuration   
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
    db {
      host = "boot2docker"
      host = ${?POSTGRES_HOST}
      port = "5432"
      port = ${?POSTGRES_PORT}
      name = "docker"

      url = "jdbc:postgresql://"${akka-persistence-jdbc.slick.db.host}":"${akka-persistence-jdbc.slick.db.port}"/"${akka-persistence-jdbc.slick.db.name}
      user = "docker"
      password = "docker"
      driver = "org.postgresql.Driver"
      keepAliveConnection = on
      numThreads = 2
      queueSize = 100
    }
  }

  tables {
    journal {
      tableName = "journal"
      schemaName = ""
      columnNames {
        persistenceId = "persistence_id"
        sequenceNumber = "sequence_number"
        created = "created"
        tags = "tags"
        message = "message"
      }
    }

    deletedTo {
      tableName = "deleted_to"
      schemaName = ""
      columnNames = {
        persistenceId = "persistence_id"
        deletedTo = "deleted_to"
      }
    }

    snapshot {
      tableName = "snapshot"
      schemaName = ""
      columnNames {
        persistenceId = "persistence_id"
        sequenceNumber = "sequence_number"
        created = "created"
        snapshot = "snapshot"
      }
    }
  }

  query {
    tagPrefix = "###"
  }
}
```

```sql
DROP TABLE IF EXISTS public.journal;

CREATE TABLE IF NOT EXISTS public.journal (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  created BIGINT NOT NULL,
  tags VARCHAR(255) DEFAULT NULL,
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

## MySQL configuration
```bash
akka {
  persistence {
    journal.plugin = "jdbc-journal"
    snapshot-store.plugin = "jdbc-snapshot-store"
  }
}

akka-persistence-jdbc {
  slick {
      driver = "slick.driver.MySQLDriver"
      db {
        host = "boot2docker"
        host = ${?MYSQL_HOST}
        port = "3306"
        port = ${?MYSQL_PORT}
        name = "mysql"
  
        url = "jdbc:mysql://"${akka-persistence-jdbc.slick.db.host}":"${akka-persistence-jdbc.slick.db.port}"/"${akka-persistence-jdbc.slick.db.name}
        user = "root"
        password = "root"
        driver = "com.mysql.jdbc.Driver"
        keepAliveConnection = on
        numThreads = 2
        queueSize = 100
      }
    }

  tables {
    journal {
      tableName = "journal"
      schemaName = ""
      columnNames {
        persistenceId = "persistence_id"
        sequenceNumber = "sequence_number"
        created = "created"
        tags = "tags"
        message = "message"
      }
    }

    deletedTo {
      tableName = "deleted_to"
      schemaName = ""
      columnNames = {
        persistenceId = "persistence_id"
        deletedTo = "deleted_to"
      }
    }

    snapshot {
      tableName = "snapshot"
      schemaName = ""
      columnNames {
        persistenceId = "persistence_id"
        sequenceNumber = "sequence_number"
        created = "created"
        snapshot = "snapshot"
      }
    }
  }

  query {
    tagPrefix = "###"
  }
}
```

```sql
DROP TABLE IF EXISTS journal;

CREATE TABLE IF NOT EXISTS journal (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  created BIGINT NOT NULL,
  tags VARCHAR(255) DEFAULT NULL,
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

## Oracle configuration
```bash
akka {
  persistence {
    journal.plugin = "jdbc-journal"
    snapshot-store.plugin = "jdbc-snapshot-store"
  }
}

akka-persistence-jdbc {
  slick {
    driver = "com.typesafe.slick.driver.oracle.OracleDriver"
    db {
      host = "boot2docker"
      host = ${?ORACLE_HOST}
      port = "1521"
      port = ${?ORACLE_PORT}
      name = "xe"

      url = "jdbc:oracle:thin:@//"${akka-persistence-jdbc.slick.db.host}":"${akka-persistence-jdbc.slick.db.port}"/"${akka-persistence-jdbc.slick.db.name}
      user = "system"
      password = "oracle"
      driver = "oracle.jdbc.OracleDriver"
      keepAliveConnection = on
      numThreads = 2
      queueSize = 100
    }
  }

  tables {
    journal {
      tableName = "journal"
      schemaName = "SYSTEM"
      columnNames {
        persistenceId = "persistence_id"
        sequenceNumber = "sequence_number"
        created = "created"
        tags = "tags"
        message = "message"
      }
    }

    deletedTo {
      tableName = "deleted_to"
      schemaName = "SYSTEM"
      columnNames = {
        persistenceId = "persistence_id"
        deletedTo = "deleted_to"
      }
    }

    snapshot {
      tableName = "snapshot"
      schemaName = "SYSTEM"
      columnNames {
        persistenceId = "persistence_id"
        sequenceNumber = "sequence_number"
        created = "created"
        snapshot = "snapshot"
      }
    }
  }

  query {
    tagPrefix = "###"
  }
}
```

```sql
CREATE TABLE "journal" (
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" NUMERIC NOT NULL,
  "created" NUMERIC NOT NULL,
  "tags" VARCHAR(255) DEFAULT NULL,
  "message" BLOB NOT NULL,
  PRIMARY KEY("persistence_id", "sequence_number")
);

CREATE TABLE "deleted_to" (
  "persistence_id" VARCHAR(255) NOT NULL,
  "deleted_to" NUMERIC NOT NULL
);

CREATE TABLE "snapshot" (
  "persistence_id" VARCHAR(255) NOT NULL,
  "sequence_number" NUMERIC NOT NULL,
  "created" NUMERIC NOT NULL,
  "snapshot" BLOB NOT NULL,
  PRIMARY KEY ("persistence_id", "sequence_number")
);
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
import akka.persistence.query.{ PersistenceQuery, EventEnvelope }
import akka.persistence.jdbc.query.journal.JdbcReadJournal

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()(system)
val readJournal: JdbcReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

val willNotCompleteTheStream: Source[EventEnvelope, Unit] = readJournal.eventsByPersistenceId("some-persistence-id", 0L, Long.MaxValue)

val willCompleteTheStream: Source[EventEnvelope, Unit] = readJournal.currentEventsByPersistenceId("some-persistence-id", 0L, Long.MaxValue)
```

You can retrieve a subset of all events by specifying `fromSequenceNr` and `toSequenceNr` or use `0L` and `Long.MaxValue` respectively to retrieve all events. Note that the corresponding sequence number of each event is provided in the `EventEnvelope`, which makes it possible to resume the stream at a later point from a given sequence number.

The returned event stream is ordered by sequence number, i.e. the same order as the PersistentActor persisted the events. The same prefix of stream elements (in same order) are returned for multiple executions of the query, except for when events have been deleted.

The stream is completed with failure if there is a failure in executing the query in the backend journal.

## EventsByTag and CurrentEventsByTag
`eventsByTag` (not yet supported) and `currentEventsByTag` are used for retrieving events that were marked with a given 
`tag`, e.g. all domain events of an Aggregate Root type.

```scala
import akka.actor.ActorSystem
import akka.stream.{Materializer, ActorMaterializer}
import akka.stream.scaladsl.Source
import akka.persistence.query.{ PersistenceQuery, EventEnvelope }
import akka.persistence.jdbc.query.journal.JdbcReadJournal

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()(system)
val readJournal: JdbcReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

val willCompleteTheStream: Source[EventEnvelope, Unit] = readJournal.currentEventsByTag("apple", 0L)
```

To tag events you'll need to create an [Event Adapter](http://doc.akka.io/docs/akka/2.4.1/scala/persistence.html#event-adapters-scala) 
that will wrap the event in a [akka.persistence.journal.Tagged](http://doc.akka.io/api/akka/2.4.1/#akka.persistence.journal.Tagged) 
class with the given tags. The `Tagged` class will instruct `akka-persistence-jdbc` to tag the event with the given set of tags.
The persistence plugin will __not__ store the `Tagged` class in the journal. It will strip the `tags` and `payload` from the `Tagged` class,
and use the class only as an instruction to tag the event with the given tags and store the `payload` in the 
`message` field of the journal table. 

```scala
import akka.persistence.journal.WriteEventAdapter
import akka.persistence.journal.Tagged
 
class MyTaggingEventAdapter extends WriteEventAdapter {
  val colors = Set("green", "black", "blue")
  override def toJournal(event: Any): Any = event match {
    case s: String ⇒
      var tags = colors.foldLeft(Set.empty[String]) { (acc, c) ⇒
        if (s.contains(c)) acc + c else acc
      }
      if (tags.isEmpty) event
      else Tagged(event, tags)
    case _ ⇒ event
  }
 
  override def manifest(event: Any): String = ""
}
```

You can retrieve a subset of all events by specifying offset, or use 0L to retrieve all events with a given tag. 
The offset corresponds to an ordered sequence number for the specific tag. Note that the corresponding offset of each 
event is provided in the EventEnvelope, which makes it possible to resume the stream at a later point from a given offset.

In addition to the offset the EventEnvelope also provides persistenceId and sequenceNr for each event. The sequenceNr is 
the sequence number for the persistent actor with the persistenceId that persisted the event. The persistenceId + sequenceNr 
is an unique identifier for the event.

The returned event stream contains only events that correspond to the given tag, and is ordered by the creation time of the events, 
The same stream elements (in same order) are returned for multiple executions of the same query. Deleted events are not deleted 
from the tagged event stream. 

# Usage
The user manual has been moved to [the wiki](https://github.com/dnvriend/akka-persistence-jdbc/wiki)

# What's new?
For the full list of what's new see [this wiki page] (https://github.com/dnvriend/akka-persistence-jdbc/wiki/Version-History).

## 2.1.0 (2016-01-24) 
 - Schema change for the journal table, added two columns, `tags` and `created`, please update your schema.
 - Support for the `currentEventsByTag` query, the tagged events will be sorted by event creation time.
 - Table column names are configurable.

## 2.0.4 (2016-01-22)
 - Using the typesafe config for the Slick database configuration,
 - Uses HikariCP as the connection pool,
 - Table names and schema names are configurable,
 - Akka Stream 2.0.1 -> 2.0.1
 - Tested with Oracle XE 

## 2.0.3 (2016-01-18)
 - Optimization for the `eventsByPersistenceId` and `allPersistenceIds` queries. 

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
