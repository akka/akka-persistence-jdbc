# akka-persistence-jdbc
Akka-persistence-jdbc is a plugin for akka-persistence that asynchronously writes journal and snapshot entries entries to a configured JDBC store. It supports writing journal messages and snapshots to two tables: the `journal` table and the `snapshot` table.

Service | Status | Description
------- | ------ | -----------
License | [![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) | Apache 2.0
Bintray | [![Download](https://api.bintray.com/packages/dnvriend/maven/akka-persistence-jdbc/images/download.svg) ](https://bintray.com/dnvriend/maven/akka-persistence-jdbc/_latestVersion) | Latest Version on Bintray

## Slick Extensions Licensing Changing to Open Source
The [Typesafe/Lightbend Slick Extensions](http://slick.typesafe.com/doc/3.1.1/extensions.html) have become [open source as 
of 1 februari 2016 as can read from the slick new website](http://slick.typesafe.com/news/2016/02/01/slick-extensions-licensing-change.html),
this means that you can use akka-persistence-jdbc with no commercial license from Typesafe/Lightbend when used with `Oracle`, `IBM DB2` or 
`Microsoft SQL Server`. Thanks [Lightbend](http://www.lightbend.com/)! Of course you will neem a commercial license from
your database vendor. 

Alternatively you can opt to use [Postgresql](http://www.postgresql.org/), which is the most advanced open source database 
available, with some great features, and it works great together with akka-persistence-jdbc.
                                 
## New release
The latest version is `v2.2.11` and breaks backwards compatibility with `v1.x.x` in a big way. New features:

- It uses [Typesafe/Lightbend Slick](http://slick.typesafe.com/) as the database backend,
  - Using the typesafe config for the Slick database configuration,
  - Uses HikariCP for the connection pool,
  - It has been tested against Postgres, MySQL and Oracle only,
  - It has an option to store journal and snapshot entries in memory, which is useful for testing,
  - It uses a new database schema, dropping some columns and changing the column types,
  - It writes the journal and snapshot entries as byte arrays,
- It relies on [Akka Serialization](http://doc.akka.io/docs/akka/2.4.1/scala/serialization.html),
  - For serializing, please split the domain model from the storage model, and use a binary format for the storage model that support schema versioning like [Google's protocol buffers](https://developers.google.com/protocol-buffers/docs/overview), as it is used by Akka Persistence, and is available as a dependent library. For an example on how to use Akka Serialization with protocol buffers, you can examine the [akka-serialization-test](https://github.com/dnvriend/akka-serialization-test) study project,
- It supports the `Persistence Query` interface for both Java and Scala thus providing a universal asynchronous stream based query interface,
- Table, column and schema names are configurable, but note, if you change those, you'll need to change the DDL scripts.

## Installation
Add the following to your `build.sbt`:

```scala
resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/maven-releases/"

resolvers += "dnvriend at bintray" at "http://dl.bintray.com/dnvriend/maven"

libraryDependencies += "com.github.dnvriend" %% "akka-persistence-jdbc" % "2.2.11"
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
   
## DataSource lookup by JNDI name
The plugin uses `slick` as the database access library. Slick [supports jndi](http://slick.typesafe.com/doc/3.1.1/database.html#using-a-jndi-name)
for looking up [DataSource](http://docs.oracle.com/javase/7/docs/api/javax/sql/DataSource.html)s. 

To enable the JNDI lookup, you must add the following to your `application.conf`:

```bash
akka-persistence-jdbc {
  slick {
    driver = "slick.driver.PostgresDriver"
    jndiName = "java:jboss/datasources/PostgresDS"   
  }
}
```
   
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
    separator = ","
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
    separator = ","
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
    separator = ","
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

## InMemory Configuration
The akka-persistence-jdbc also has an in-memory storage option. For practical reasons, ie. the plugin may already 
be on the classpath. This is useful for testing. I would advice not to use it in production systems, because it 
uses memory, and the data is persisted in volatile memory, which means that after a JVM restart, all data is lost.
By default the in-memory option is disabled.

Add the following to `application.conf` enable the in-memory option:

```scala
akka-persistence-jdbc {
  inMemory = true
}
```

To disable the in-memory option:

```scala
akka-persistence-jdbc {
  inMemory = false
}
```

## How to get the ReadJournal using Scala
The `ReadJournal` is retrieved via the `akka.persistence.query.PersistenceQuery` extension:

```scala
import akka.persistence.query.PersistenceQuery
import akka.persistence.jdbc.query.journal.scaladsl.JdbcReadJournal
 
val readJournal: JdbcReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)
```

## How to get the ReadJournal using Java
The `ReadJournal` is retrieved via the `akka.persistence.query.PersistenceQuery` extension:

```java
import akka.persistence.query.PersistenceQuery
import akka.persistence.jdbc.query.journal.javadsl.JdbcReadJournal

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
import akka.persistence.jdbc.query.journal.scaladsl.JdbcReadJournal

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
import akka.persistence.jdbc.query.journal.scaladsl.JdbcReadJournal

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
import akka.persistence.jdbc.query.journal.scaladsl.JdbcReadJournal

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()(system)
val readJournal: JdbcReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

val willNotCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.eventsByTag("apple", 0L)

val willCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.currentEventsByTag("apple", 0L)
```

To tag events you'll need to create an [Event Adapter](http://doc.akka.io/docs/akka/2.4.1/scala/persistence.html#event-adapters-scala) 
that will wrap the event in a [akka.persistence.journal.Tagged](http://doc.akka.io/api/akka/2.4.1/#akka.persistence.journal.Tagged) 
class with the given tags. The `Tagged` class will instruct `akka-persistence-jdbc` to tag the event with the given set of tags.
The persistence plugin will __not__ store the `Tagged` class in the journal. It will strip the `tags` and `payload` from the `Tagged` class,
and use the class only as an instruction to tag the event with the given tags and store the `payload` in the 
`message` field of the journal table. 

```scala
import akka.persistence.journal.{ Tagged, WriteEventAdapter }
import com.github.dnvriend.Person.{ LastNameChanged, FirstNameChanged, PersonCreated }

class TaggingEventAdapter extends WriteEventAdapter {
  override def manifest(event: Any): String = ""

  def withTag(event: Any, tag: String) = Tagged(event, Set(tag))

  override def toJournal(event: Any): Any = event match {
    case _: PersonCreated ⇒
      withTag(event, "person-created")
    case _: FirstNameChanged ⇒
      withTag(event, "first-name-changed")
    case _: LastNameChanged ⇒
      withTag(event, "last-name-changed")
    case _ ⇒ event
  }
}
```

The `EventAdapter` must be registered by adding the following to the root of `application.conf` Please see the 
[demo-akka-persistence-jdbc](https://github.com/dnvriend/demo-akka-persistence-jdbc) project for more information.

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

You can retrieve a subset of all events by specifying offset, or use 0L to retrieve all events with a given tag. 
The offset corresponds to an ordered sequence number for the specific tag. Note that the corresponding offset of each 
event is provided in the EventEnvelope, which makes it possible to resume the stream at a later point from a given offset.

In addition to the offset the EventEnvelope also provides persistenceId and sequenceNr for each event. The sequenceNr is 
the sequence number for the persistent actor with the persistenceId that persisted the event. The persistenceId + sequenceNr 
is an unique identifier for the event.

The returned event stream contains only events that correspond to the given tag, and is ordered by the creation time of the events, 
The same stream elements (in same order) are returned for multiple executions of the same query. Deleted events are not deleted 
from the tagged event stream. 

## EventsByPersistenceIdAndTag and CurrentEventsByPersistenceIdAndTag
`eventsByPersistenceIdAndTag` and `currentEventsByPersistenceIdAndTag` is used for retrieving specific events identified 
by a specific tag for a specific PersistentActor identified by persistenceId. These two queries basically are 
convenience operations that optimize the lookup of events because the database can efficiently filter out the initial 
persistenceId/tag combination. 

```scala
import akka.actor.ActorSystem
import akka.stream.{Materializer, ActorMaterializer}
import akka.stream.scaladsl.Source
import akka.persistence.query.{ PersistenceQuery, EventEnvelope }
import akka.persistence.jdbc.query.journal.scaladsl.JdbcReadJournal

implicit val system: ActorSystem = ActorSystem()
implicit val mat: Materializer = ActorMaterializer()(system)
val readJournal: JdbcReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

val willNotCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.eventsByPersistenceIdAndTag("fruitbasket", "apple", 0L)

val willCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.currentEventsByPersistenceIdAndTag("fruitbasket", "apple", 0L)
```

# Custom DAO Implementation
As of `2.2.11` the plugin supports loading a custom DAO for the journal and snapshot logic. Why would you want to have your own 
DAO. If you can't answer this answer yourself, please keep using the default implementations that come out of the box.

By means of configuration in `application.conf` a custom DAO can be configured, below the default DAOs are shown:

```bash
serialization {
    journal = on // alter only when using a custom dao
    snapshot = on // alter only when using a custom dao
  }

  dao {
    journal = "akka.persistence.jdbc.dao.DefaultJournalDao"
    snapshot = "akka.persistence.jdbc.dao.DefaultSnapshotDao"
  }
```

You should implement a custom DAO if you wish to alter the default persistency strategy in any way, but you wish to reuse 
all the logic that the plugin already has in place, eg. the Akka Persistence Query API. For example, the default implementation
serializes messages and stores them as a byte array in a database blob. 

But this is not the only way to store information in a database. For example, you could store messages with full type information
as a normal database row. By implementing a JournalLog table that stores all persistenceId, sequenceNumber and event type (discriminator field),
and storing the event data in another table with full typing, it is possible to store and retrieve journal and snapshot entries. 

You only have to implement two interfaces `akka.persistence.jdbc.dao.JournalDao` and/or `akka.persistence.jdbc.dao.SnapshotDao`. 

For example, take a look at the following two custom DAOs:
 
```scala
class MyCustomJournalDao(db: JdbcBackend#Database, val profile: JdbcProfile, system: ActorSystem) extends JournalDao {
    // snip 
}

class MyCustomSnapshotDao(db: JdbcBackend#Database, val profile: JdbcProfile, system: ActorSystem) extends SnapshotDao {
    // snip
}
```

As you can see, the custom DAOs get a Slick database, a slick profile and an ActorSystem injected after constructed. You should register the Fully Qualified Class Name in `application.conf` so that the custom DAOs will be used.

For more information please review the two default implementations `akka.persistence.jdbc.dao.DefaultJournalDao` and `akka.persistence.jdbc.dao.DefaultSnapshotDao` or the demo custom custom `CounterJournalDao` example from the [demo-akka-persistence](https://github.com/dnvriend/demo-akka-persistence-jdbc/blob/master/src/main/scala/com/github/dnvriend/dao/CounterJournalDao.scala) site.

# What's new?
## 2.2.11 (2016-03-09)
  - Configurable custom DAO implementation through configuration,
  - Enable/Disable Serialization, the default journal and snapshot DAO rely on serialization, only disable when you known what you are doing, 
  - Scala 2.11.7 -> 2.11.8

## 2.2.10 (2016-03-04)
  - Fix for parsing the schema name configuration for the `deleted_to` and `snapshot` table configuration.  

## 2.2.9 (2016-03-03)
  - Fix for propagating serialization errors to akka-persistence so that any error regarding the persistence of messages will be handled by the callback handler of the Persistent Actor; `onPersistFailure`.  

## 2.2.8 (2016-02-18)
  - Added InMemory option for journal and snapshot storage, for testing

## 2.2.7 (2016-02-17)
  - Akka 2.4.2-RC3 -> 2.4.2

## 2.2.6 (2016-02-13)
  - akka-persistence-jdbc-query 1.0.0 -> 1.0.1

## 2.2.5 (2016-02-13)
  - Akka 2.4.2-RC2 -> 2.4.2-RC3

## 2.2.4 (2016-02-08)
  - Compatibility with Akka 2.4.2-RC2
  - Refactored the akka-persistence-query extension interfaces to its own jar: `"com.github.dnvriend" %% "akka-persistence-jdbc-query" % "1.0.0"`
  
## 2.2.3 (2016-01-29)
  - Refactored the akka-persistence-query package structure. It wasn't optimized for use with javadsl. Now the name of the ReadJournal is `JdbcReadJournal` for both Java and Scala, only the package name has been changed to reflect which language it is for. 
  - __Scala:__ akka.persistence.jdbc.query.journal.`scaladsl`.JdbcReadJournal
  - __Java:__ akka.persistence.jdbc.query.journal.`javadsl`.JdbcReadJournal

## 2.2.2 (2016-01-28)
  - Support for looking up DataSources using JNDI

## 2.2.1 (2016-01-28)
  - Support for the akka persistence query JavaDSL

## 2.2.0 (2016-01-26)
  - Compatibility with Akka 2.4.2-RC1

## 2.1.2 (2016-01-25)
  - Support for the `currentEventsByPersistenceIdAndTag` and `eventsByPersistenceIdAndTag` queries

## 2.2.1 (2016-01-24)
  - Support for the `eventsByTag` live query
  - Tags are now separated by a character, and not by a tagPrefix
  - Please note the configuration change. 

## 2.1.0 (2016-01-24) 
 - Support for the `currentEventsByTag` query, the tagged events will be sorted by event creation time.
 - Table column names are configurable.
 - Schema change for the journal table, added two columns, `tags` and `created`, please update your schema.

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
 
## 1.2.1 (2015-10-12) 
 - Merged PR #27 [Andrey Kouznetsov](https://github.com/prettynatty) don't fail on asyncWrite with empty messages, thanks! 
## 1.2.0 (2015-10-02)
 - Compatibility with Akka 2.4.0
 - Akka 2.4.0-RC3 -> 2.4.0
 - scalikejdbc 2.2.7 -> 2.2.8
 - No obvious optimalizations are applied, and no schema refactorings are needed (for now)
 - Fully backwards compatible with akka-persistence-jdbc v1.1.8's schema and configuration 

## 1.2.0-RC3 (2015-09-17) 
 - Compatibility with Akka 2.4.0-RC3
 - No obvious optimalizations are applied, and no schema refactorings are needed (for now)
 - Please note; schema, serialization (strategy) and code refactoring will be iteratively applied on newer release of the 2.4.0-xx branch, but for each step, a migration guide and SQL scripts will be made available.
 - Use the following library dependency: "com.github.dnvriend" %% "akka-persistence-jdbc" % "1.2.0-RC3"
 - Fully backwards compatible with akka-persistence-jdbc v1.1.8's schema and configuration 

## 1.2.0-RC2 (2015-09-07) 
 - Compatibility with Akka 2.4.0-RC2
 - No obvious optimalizations are applied, and no schema refactorings are needed (for now)
 - Please note; schema, serialization (strategy) and code refactoring will be iteratively applied on newer release of the 2.4.0-xx branch, but for each step, a migration guide and SQL scripts will be made available.
 - Use the following library dependency: "com.github.dnvriend" %% "akka-persistence-jdbc" % "1.2.0-RC2"
 - Fully backwards compatible with akka-persistence-jdbc v1.1.8's schema and configuration 

## 1.1.9 (2015-10-12) - Akka v2.3.x
 - scala 2.10.5 -> 2.10.6
 - akka 2.3.13 -> 2.3.14
 - scalikejdbc 2.2.7 -> 2.2.8
 
## 1.1.8 (2015-09-04)
 - Compatibility with Akka 2.3.13
 - Akka 2.3.12 -> 2.3.13

## 1.1.7 (2015-07-13)
 - Scala 2.11.6 -> 2.11.7
 - Akka 2.3.11 -> 2.3.12
 - ScalaTest 2.1.4 -> 2.2.4

## 1.1.6 (2015-06-22)
 - ScalikeJdbc 2.2.6 -> 2.2.7
 - Issue #22 `persistenceId` missing in `JournalTypeConverter.unmarshal(value: String)` signature; added a second parameter `persistenceId: String`, note this breaks the serialization API.

## 1.1.5 (2015-05-12)
 - Akka 2.3.10 -> 2.3.11
 - MySQL snapshot statement now uses `INSERT INTO .. ON DUPLICATE UPDATE` for `upserts`
 - Merged Issue #21 [mwkohout](https://github.com/mwkohout) Use a ParameterBinder to pass snapshot into the merge statement and get rid of the stored procedure, thanks!

## 1.1.4 (2015-05-06)
 - ScalikeJDBC 2.2.5 -> 2.2.6
 - Akka 2.3.9 -> 2.3.10
 - Switched back to a Java 7 binary, to support Java 7 and higher based projects, we need a strategy though when [Scala 2.12](http://www.scala-lang.org/news/2.12-roadmap) will be released. 
 - Merged Issue #20 [mwkohout](https://github.com/mwkohout) Use apache commons codec Base64 vs the java8-only java.util.Base64 for Java 7 based projects, thanks!

## 1.1.3 (2015-04-15)
 - ScalikeJDBC 2.2.4 -> 2.2.5
 - Fixed: 'OutOfMemory error when recovering with a large number of snapshots #17'

## 1.1.2 (2015-03-21)
 - Initial support for a pluggable serialization architecture. Out of the box the plugin uses the
   `Base64JournalConverter` and `Base64SnapshotConverter` as serializers. For more information
   see the [akka-persistence-jdbc-play](https://github.com/dnvriend/akka-persistence-jdbc-play) example
   project that uses its own JSON serialization format to write journal entries to the data store.

## 1.1.1 (2015-03-17)
 - ScalikeJDBC 2.2.2 -> 2.2.4
 - Java 8 binary, so it needs Java 8, you still use Java 6 or 7, upgrade! :P
 - Using the much faster Java8 java.util.Base64 encoder/decoder
 - Bulk insert for journal entries (non-oracle only, sorry)
 - Initial support for JNDI, needs testing though
 - Merged [Paul Roman](https://github.com/romusz) Fix typo in journal log message #14, thanks!
 - Merged [Pavel Boldyrev](https://github.com/bpg) Fix MS SQL Server support #15 (can not test it though, needs Vagrant), thanks!

## 1.1.0
 - Merged [Pavel Boldyrev](https://github.com/bpg) Fix Oracle SQL `MERGE` statement usage #13 which fixes issue #9 (java.sql.SQLRecoverableException: No more data to read from socket #9), thanks!
 - Change to the Oracle schema, it needs a stored procedure definition.

## 1.0.9 (2015-01-20)
 - ScalikeJDBC 2.1.2 -> 2.2.2
 - Merged [miguel-vila](https://github.com/miguel-vila) Adds ´validationQuery´ configuration parameter #10, thanks!
 - Removed Informix support: I just don't have a working Informix docker image (maybe someone can create one and publish it?)

## 1.0.8
 - ScalikeJDBC 2.1.1 -> 2.1.2
 - Moved to bintray

## 1.0.7 (2014-09-16)
 - Merged [mwkohout](https://github.com/mwkohout) fix using Oracle's MERGE on issue #3, thanks! 

## 1.0.6 
 - Fixed - Issue3: Handling save attempts with duplicate snapshot ids and persistence ids
 - Fixed - Issue5: Connection pool is being redefined when using journal and snapshot store

## 1.0.5 (2014-08-26)
 - Akka 2.3.5 -> 2.3.6
 - ScalikeJDBC 2.1.0 -> 2.1.1

## 1.0.4 
 - Added schema name configuration for the journal and snapshot
 - Added table name configuration for the journal and snapshot
 - ScalikeJDBC 2.0.5 -> 2.1.0 
 - Akka 2.3.4 -> 2.3.5 

## 1.0.3 (2014-07-23)
 - IBM Informix 12.10 supported 

## 1.0.2 
 - Oracle XE 11g supported

## 1.0.1
 - scalikejdbc 2.0.4 -> 2.0.5
 - akka-persistence-testkit 0.3.3 -> 0.3.4

## 1.0.0 (2014-07-03)
 - Release to Maven Central

## 0.0.6
 - Tested against MySQL/5.6.19 MySQL Community Server (GPL) 
 - Tested against H2/1.4.179

## 0.0.5
 - Added the snapshot store

## 0.0.4
 -  Refactored the JdbcSyncWriteJournal so it supports the following databases:

## 0.0.3 (2014-07-01)
 - Using [Martin Krasser's](https://github.com/krasserm) [akka-persistence-testkit](https://github.com/krasserm/akka-persistence-testkit)
  to test the akka-persistence-jdbc plugin. 
 - Update to Akka 2.3.4

## 0.0.2 (2014-06-30)
 - Using [ScalikeJDBC](http://scalikejdbc.org/) as the JDBC access library instead of my home-brew one. 

## 0.0.1 (2014-05-23)
 - Initial commit

# Code of Conduct
**Contributors all agree to follow the [W3C Code of Ethics and Professional Conduct](http://www.w3.org/Consortium/cepc/).**

If you want to take action, feel free to contact Dennis Vriend <dnvriend@gmail.com>. You can also contact W3C Staff as explained in [W3C Procedures](http://www.w3.org/Consortium/pwe/#Procedures).

# License
This source code is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0). The [quick summary of what this license means is available here](https://tldrlegal.com/license/apache-license-2.0-(apache-2.0))

Have fun!
