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
// the library is available in Bintray's JCenter
resolvers += Resolver.jcenterRepo

// akka 2.5.x
libraryDependencies += "com.github.dnvriend" %% "akka-persistence-jdbc" % "3.2.0"

// akka 2.4.x
libraryDependencies += "com.github.dnvriend" %% "akka-persistence-jdbc" % "2.8.0"
```

## Contribution policy

Contributions via GitHub pull requests are gladly accepted from their original author. Along with any pull requests, please state that the contribution is your original work and that you license the work to the project under the project's open source license. Whether or not you state this explicitly, by submitting any copyrighted material via pull request, email, or other means you agree to license the material under the project's open source license and warrant that you have the legal authority to do so.

## Code of Conduct
Contributors all agree to follow the [W3C Code of Ethics and Professional Conduct][w3c-cond].

If you want to take action, feel free to contact Dennis Vriend <dnvriend@gmail.com>. You can also contact W3C Staff as explained in [W3C Procedures][w3c-proc].

## License
This source code is made available under the [Apache 2.0 License][apache]. The [quick summary of what this license means is available here](https://tldrlegal.com/license/apache-license-2.0-(apache-2.0))

## Configuration
The plugin relies on Slick 3.2.1 to do create the SQL dialect for the database in use, therefore the following must be configured in `application.conf`

Configure `akka-persistence`:
- instruct akka persistence to use the `jdbc-journal` plugin,
- instruct akka persistence to use the `jdbc-snapshot-store` plugin,

Configure `slick`:
- The following slick profiles are supported:
  - `slick.jdbc.PostgresProfile$`
  - `slick.jdbc.MySQLProfile$`
  - `slick.jdbc.H2Profile$`
  - `slick.jdbc.OracleProfile$`

## Database Schema

- [Postgres Schema](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/schema/postgres/postgres-schema.sql)
- [MySQL Schema](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/schema/mysql/mysql-schema.sql)
- [H2 Schema](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/schema/h2/h2-schema.sql)
- [Oracle Schema](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/schema/oracle/oracle-schema.sql)

## Configuration

- [Default](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/main/resources/reference.conf)
- [Postgres](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/postgres-application.conf)
- [MySQL](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/mysql-application.conf)
- [H2](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/h2-application.conf)
- [Oracle](https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/oracle-application.conf)

## DataSource lookup by JNDI name
The plugin uses `slick` as the database access library. Slick [supports jndi][slick-jndi] for looking up [DataSource][ds]s.

To enable the JNDI lookup, you must add the following to your application.conf:

```
jdbc-journal {
  slick {
    profile = "slick.jdbc.PostgresProfile$"
    jndiName = "java:jboss/datasources/PostgresDS"   
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
The plugin automatically shuts down the HikariCP connection pool only when the ActorSystem is explicitly terminated.
It is advisable to register a shutdown hook to be run when the VM exits that terminates the ActorSystem:

```scala
sys.addShutdownHook(system.terminate())
```

## Changelog  

### 3.2.0 (2018-01-22)
For use with Akka 2.5.x only, please use the 2.8.0 release for Akka 2.4 

Changes since v3.1.0
  - Added possibility for physical deletion of marked journal messages.
    To physically delete events `jdbc-journal.logicalDelete` setting should be set to `false`.
  - Akka 2.5.8 -> 2.5.9
  - Merged #139. Thanks to [Dmitriy Zakomirnyi][dmi3zkm].

### 3.1.0 (2017-12-20)
For use with Akka 2.5.x only, please use the 2.8.0 release for Akka 2.4 

Changes since v3.0.1
  - The internal events by tag implementation could perform poorly in some special cases, this has been resolved. (see #155)
  - EventsByTag queries are now executed more efficiently by avoiding re-querying data (see #149)
  - The eventsByTag and currentEventsByTag queries are now documented (in scaladoc) to explain the guarantees offered by this plugin (and its caveats).
  - Removed dependency on JournalRow in ReadJournalDao.
    This is a breaking change for everyone who implements a custom ReadJournalDao.
  - Updated Scala versions to 2.12.4 / 2.11.12
  - Akka 2.5.6 -> 2.5.8
  - Merged #139, #140, #141, #143, #145, #146, #148, #14, #152, #153, #154, #157, #158. Thanks to
    [Renato Cavalcanti][renatocaval], [Ruud Welling][WellingR], [Sean Sullivan][sullis].

### 3.0.1 (2017-10-27)
For use with Akka 2.5.x only, please use the 2.8.0 release for Akka 2.4 

Changes since v3.0.0
  - Align JournalTables with the sql schemas (issue #130)
  - Current events by tag fix (issue #124)
  - fixes for max ordering jump (issue #126)
  - Allow configuration of ask timeout when querying maxOrdering (issue #123)
  - Cache dependencies in Travis CI.
  - sbt-release 1.0.6
  - Akka 2.5.4 -> 2.5.6
  - Merged PRs #122, #127, #128, #129, #132, #133, #135. Thanks to [Jimin Hsieh][jiminhsieh],
    [Renato Cavalcanti][renatocaval], [Sean Sullivan][sullis],
    [Ruud Welling][WellingR], [Adam Chaulk](acchaulk).


### 2.8.0 (2017-10-27)
For use with Akka 2.4.x only, please use release 3.0.1 with Akka 2.5

Changes since v2.7.0
  - **This is a breaking change!**  EventsByTag query treats the offset as exclusive instead of inclusive. All users of
    the eventsByTag query can now reuse the `Offset` from the last received `EventEnvelope` to resume the query.
  - Align JournalTables with the sql schemas (issue #130)
  - Current events by tag fix (issue #124) 
  - fixes for max ordering jump (issue #126)
  - Allow configuration of ask timeout when querying maxOrdering (issue #123)
  - sbt-release 1.0.6
  - Merged PRs #122, #127, #128, #129, #132, #133. Thanks to [Jimin Hsieh][jiminhsieh],
    [Renato Cavalcanti][renatocaval], [Sean Sullivan][sullis],
    [Ruud Welling][WellingR], [Adam Chaulk](acchaulk).

### 3.0.0 (2017-08-29)
Changes since v2.5.2.0
  - Akka 2.5 release only, please use the 2.7.x release for Akka 2.4 
  - EventsByTag query treats the offset as exclusive instead of inclusive. **This is a breaking change!** All users of
    the eventsByTag query can now reuse the `Offset` from the last received `EventEnvelope` to resume the query.
  - The database schemas have been updated with an extra index to improve the eventsByTag query performance
  - Fixed bug in eventsByTag and currentEventsByTag query, which could case some event to be skipped
  - Implemented batch writing for the journal which results in better throughput.
  - The reference config no longer contains environment specific defaults such as the slick profile and the databas url.
    Instead, the reference config documents how to configure these. 
  - Fixed a potential issue where actors could retrieve their latest sequence number too early upon restart.
  - Slick 3.2.0 -> 3.2.1 (Note: Slick 3.2.1 is not binary compatible with 3.2.0)
  - Akka 2.5.2 -> 2.5.4
  - Merged PRs #121, #120, #119, #117, #115, #114, #113, #111, #110. Thanks to [Jimin Hsieh][jiminhsieh],
    [Renato Cavalcanti][renatocaval], [Frederic Gendebien][frederic-gendebien], [Sean Sullivan][sullis],
    [Ruud Welling][WellingR], [Sarah Gerweck][sarahgerweck].


### 2.7.0 (2017-08-29)
Changes since v2.4.18.2
  - For use with Akka 2.4.x only, please use release 3.0.0 with Akka 2.5
  - The database schemas have been updated with an extra index to improve the eventsByTag query performance
  - Fixed bug in eventsByTag and currentEventsByTag query, which could case some event to be skipped
  - Implemented batch writing for the journal which results in better throughput.
  - The reference config no longer contains environment specific defaults such as the slick profile and the databas url.
    Instead, the reference config documents how to configure these. 
  - Fixed a potential issue where actors could retrieve their latest sequence number too early upon restart.
  - Slick 3.2.0 -> 3.2.1 (Note: Slick 3.2.1 is not binary compatible with 3.2.0)
  - Akka 2.4.18 -> 2.4.20
  - Merged PRs #121, #120, #119, #117, #115, #114, #113, #111, #110. Thanks to [Jimin Hsieh][jiminhsieh],
    [Renato Cavalcanti][renatocaval], [Frederic Gendebien][frederic-gendebien], [Sean Sullivan][sullis],
    [Ruud Welling][WellingR], [Sarah Gerweck][sarahgerweck].


### 2.4.18.2 (2017-06-09)
  - Fixed Issue #106 'JdbcReadJournal in javadsl does not support CurrentEventsByTagQuery2 and EventsByTagQuery2'

### 2.5.2.0 (2017-06-09)
  - Merged PR #105 [aenevala][aenevala] Added support for event adapters on query side for 2.5.2.0, thanks!
  - Akka 2.5.1 -> 2.5.2

### 2.4.18.1 (2017-06-04)
  - Merged PR #103 [aenevala][aenevala] Added support for event adapters on query side, thanks!

### 2.5.1.0 (2017-05-03)
  - Akka 2.5.0 -> 2.5.1

### 2.4.18.0 (2017-05-03)
  - Akka 2.4.17 -> 2.4.18
  - Merged PR #88 [Andrey Kouznetsov][kouznetsov] Compiled inserts, thanks!
  - Tweaked threads, maxConnections and minConnections to leverage [slick issue #1461 - fixes issue 1274: Slick deadlock](https://github.com/slick/slick/pull/1461)

### 2.5.0.0 (2017-04-13)
  - Merged PR #97 [Dan Di Spaltro][dispalt] Update to akka-2.5.0-RC2, thanks!
  - Akka 2.5.0-RC2 -> Akka 2.5.0

### 2.4.17.1 (2017-02-24)
  - Slick 3.1.1 -> 3.2.0
  - Scala 2.11.8 and 2.12.1 support
  - The following slick profiles are supported:
    - `slick.jdbc.PostgresProfile$`
    - `slick.jdbc.MySQLProfile$`
    - `slick.jdbc.H2Profile$`
    - `slick.jdbc.OracleProfile$`

### 2.4.17.0.3.2.0-RC1
  - Slick 3.2.0-RC1 test release
  - Akka 2.4.17
  - Only availabe on Bintray's JCenter

### 2.4.17.0 (2016-02-12)
  - New versioning scheme; now using the version of Akka with the akka-persistence-inmemory version appended to it, starting from `.0`
  - Akka 2.4.16 -> 2.4.17

### 2.6.12-3.2.0-M2 (2016-12-21)
  - Special thanks to [joseblas][joseblas] for PR #85: Slick 3.2.0-M1 migration, thanks!
  - Special thanks to [Timothy Klim][timothyklim] for PR #86: Upgrade slick to 3.2.0-M2, thanks!
  - Slick 3.2.0-M2 test release
  - Scala 2.11.8 and 2.12.1 build
  - Only availabe on Bintray's JCenter
  - The following slick profiles are supported:
    - `slick.jdbc.PostgresProfile$`
    - `slick.jdbc.MySQLProfile$`
    - `slick.jdbc.H2Profile$`
    - `slick.jdbc.OracleProfile$`

### 2.6.12 (2016-12-20)
  - Akka 2.4.14 -> 2.4.16

### 2.6.11 (2016-12-09)
  - Merged PR #3 [Sergey Kisel][skisel] - Freeslick support to use it with Oracle, thanks!

### 2.6.10 (2016-11-22)
  - Akka 2.4.13 -> 2.4.14

### 2.6.9 (2016-11-20)
  - Akka 2.4.12 -> 2.4.13

### 2.6.8 (2016-11-03)
  - Akka 2.4.10 -> 2.4.12
  - Fixed 'Snapshot storage BLOB handling' by [Sergey Kisel][skisel], thanks!
  - Filter out events that have already been deleted.
  - Removed the _non-official_ and __never-to-be-used__ bulk loading interface.
  - Support for the new queries `CurrentEventsByTagQuery2` and `EventsByTagQuery2`, please read the akka-persistence-query documentation to see what has changed.
  - The akka-persistence-jdbc plugin only supports the `akka.persistence.query.NoOffset` and the `akka.persistence.query.Sequence` offset types.
  - There is no support for the `akka.persistence.query.TimeBasedUUID` offset type. When used, akka-persistence-jdbc will throw an IllegalArgumentException if offered to the read-journal.

### 2.6.7 (2016-09-07)
  - Merged PR #75 [jroper][jroper] - Removed binary dependency on slick-extensions, thanks!
  - Please note, slick-extensions 3.1.0 are open source, but the license didn't change, so you cannot use it for free, you still need a [Lightbend Subscription](https://www.lightbend.com/platform/subscription).
  - Akka 2.4.9 -> 2.4.10

### 2.6.6 (2016-08-22)
  - Merged PR #66 [monktastic][monktastic], eventsByPersistenceId should terminate when toSequenceNr is reached, thanks!

### 2.6.5 (2016-08-20)
  - Akka 2.4.9-RC2 -> 2.4.9

### 2.6.5-RC2 (2016-08-06)
  - Akka 2.4.9-RC1 -> 2.4.9-RC2

### 2.6.5-RC1 (2016-08-03)
  - Akka 2.4.8 -> 2.4.9-RC1

### 2.6.4 (2016-07-30)
  - Merged PR #62 [jtysper][jtysper], Fix Oracle support, thanks!

### 2.6.3 (2016-07-27)
  - Merged PR #61 [Nikolay Tatarinov][rockjam], Sql optimizations, thanks!

### 2.6.2 (2016-07-26)
  - Fix for issue #60 where an immutable.Vector was trying to be matched by the serializer in TrySeq stage.

### 2.6.1 (2016-07-23)
  - Support for the __non-official__ bulk loading interface [akka.persistence.query.scaladsl.EventWriter](https://github.com/dnvriend/akka-persistence-query-writer/blob/master/src/main/scala/akka/persistence/query/scaladsl/EventWriter.scala)
    added. I need this interface to load massive amounts of data, that will be processed by many actors, but initially I just want to create and store one or
    more events belonging to an actor, that will handle the business rules eventually. Using actors or a shard region for that matter, just gives to much
    actor life cycle overhead ie. too many calls to the data store. The `akka.persistence.query.scaladsl.EventWriter` interface is non-official and puts all
    responsibility of ensuring the integrity of the journal on you. This means when some strange things are happening caused by wrong loading of the data,
    and therefore breaking the integrity and ruleset of akka-persistence, all the responsibility on fixing it is on you, and not on the Akka team.

### 2.6.0 (2016-07-17)
  - Removed the `deleted_to` and `created` columns of the `journal` table to become compatible with
   `akka-persistence-query` spec that states that all messages should be replayed, even deleted ones
  - New schema's are available for [postgres][postgres-schema], [mysql][mysql-schema] and [h2][h2-schema]
  - No need for Query Publishers with the new akka-streams API
  - Codacy code cleanup
  - There is still no support for Oracle since the addition of the ordering SERIAL column which Oracle does not support. Help to add Oracle support is appreciated.

### 2.5.2 (2016-07-03)
  - The `eventsByTag` query should now be fixed.

### 2.5.1 (2016-07-03)
  - There is no 2.5.1; error while releasing

### 2.5.0 (2016-06-29)
  - Changed the database schema to include two new columns, an `ordering` and a `deleted` column. Both fields are needed
    to support the akka-persistence-query API. The `ordering` column is needed to register the total ordering of events
    an is used for the offset for both `*byTag` queries. The deleted column is not yet used.
  - Known issue: will not work on Oracle (yet).

### 2.4.1 (2016-06-20)
  - Merged PR #57 [Filipe Cristóvão][fcristovao], Added support for the H2 database, thanks!  

### 2.4.0 (2016-06-19)
  - Merged PR #55 [Filipe Cristóvão][fcristovao], Redesign of the serializer/deserializer to make it possible to override it to implement your own serialization strategy, thanks!  
  - This is potentially a breaking change for users that implement there own DAO or extend and override some of the features of the default one:
    1. The DAO package has been change from `akka.persistence.jdbc.dao.bytea` to `akka.persistence.jdbc.dao.bytea.journal`
    2. The DAOs constructor has changed from (db: Database, profile: JdbcProfile, cfg: JournalConfig) to (db: Database, profile: JdbcProfile, cfg: JournalConfig, serialization: Serialization), so it gets the akka.serialization.Serialization injected.
  - Removed the `jdbc-journal.serialization`, `jdbc-snapshot-store.serialization` and `jdbc-read-journal.serialization` setting as the DAOs have to implement their own serialization strategy.
  - The following interfaces `akka.persistence.jdbc.dao.JournalDao`, `akka.persistence.jdbc.dao.ReadJournalDao` and `akka.persistence.jdbc.dao.SnapshotDao` have been changed as the DAOs have to implement their own strategy, they'll have to work with `AtomicWrite`, `PersistentRepr` and `Any` as types.   

### 2.3.3 (2016-06-13)
  - Fix for the async query `eventsByTag` that failed when using an Oracle database.

### 2.3.2 (2016-06-12)
  - This release has a configuration how the the slick database driver gets resolved. The following driver names must be used:
    - `slick.driver.PostgresDriver$`
    - `slick.driver.MySQLDriver$`
    - `com.typesafe.slick.driver.oracle.OracleDriver$`
  - The journal, snapshot and readjournal plugins now all use defaults as stated in the reference.conf, it is not necessary to define properties when using a plugin-id that has not been defined in reference.conf          

### 2.3.1 (2016-06-10)
  - Async queries should take a max number of elements from the result set according to the
  `jdbc-read-journal.max-buffer-size` configuration. This should result in better memory usage and better IO performance.

### 2.3.0 (2016-06-10)
  - This is a feature, configuration and (maybe) API breaking release when you rely on the DAO's, my apologies.
  - Killed some [feature-creep], this will result in a better design of the plugin.
    - Removed support for the Varchar (base64/text based serialization),
    - Removed support for the in-memory storage, please use the [akka.persistence-inmemory][inmemory] plugin,
    - Removed the queries `eventsByPersistenceIdAndTag` and `currentEventsByPersistenceIdAndTag` as they are not supported by Akka natively and can be configured by filtering the event stream.
  - Implemented async queries, fixes issue #53 All async queries do not work as expected
  - Implemented akka persistence plugin scoping strategy, fixes issue #42 Make it possible to have multiple instances of the plugin (configured differently)

### 2.2.25 (2016-06-08)
  - Merged PR #54 [Charith Ellawala][ellawala] Honour the schemaName setting for the snapshot table, thanks!
  - Compiling for Java 8 as Akka 2.4.x dropped support for Java 6 and 7 and only works on Java 8 and above  

### 2.2.24 (2016-06-05)
  - Akka 2.4.6 -> 2.4.7

### 2.2.23 (2016-05-25)
  - Akka 2.4.5 -> 2.4.6

### 2.2.22 (2016-05-18)
  - Merged PR #52 [Gopal Shah][shah] for issue [#44 - Unable to differentiate between persistence failures and serialization issues](https://github.com/dnvriend/akka-persistence-jdbc/issues/51), thanks!
  - Akka 2.4.4 -> 2.4.5

### 2.2.21 (2016-04-30)
  - Disabled the default dependency on HikariCP-Java6 v2.3.7,
  - Added dependency on HikariCP v2.4.6 for better performance and bug fixes

### 2.2.20 (2016-04-29)
  - Merged PR #50 [Andrey Kouznetsov][kouznetsov] for issue [#44 - Leaking connections](https://github.com/dnvriend/akka-persistence-jdbc/issues/44), thanks!

### 2.2.19 (2016-04-26)
  - Merged PR #46 [Andrey Kouznetsov][kouznetsov] Significant performance boost by using compiled queries, thanks!
  - Merged PR #47 [Andrey Kouznetsov][kouznetsov] Ability to get Database instance from JNDI, thanks!
  - **_Disable the async queries as they are implemented very sketchy, please use the synchronous query API with client side polling._**  

### 2.2.18 (2016-04-19)
  - Text based serialization formats

### 2.2.17 (2016-04-14)
  - Fix for [Issue #41 - Provide a way to shut-down connections explicitly](https://github.com/dnvriend/akka-persistence-jdbc/issues/41), the database connections will be automatically shut down when the ActorSystem shuts down when calling `system.terminate()` in which `system` is the ActorSystem instance.
  - Akka 2.4.3 -> 2.4.4

### 2.2.16 (2016-04-01)
  - Akka 2.4.2 -> 2.4.3

### 2.2.15 (2016-03-18)
  - Merged PR #37 [William Turner][turner] Make offset sequential on eventsByTag queries, thanks!

### 2.2.14 (2016-03-17)
  - Determine events where appropriate by using an offset using the query api was not tested and thus the implementation was incorrect. This has been corrected and the documentation altered where appropriate.

### 2.2.13 (2016-03-17)
  - Release to enable Bintray to sync with JCenter, so no big changes here  

### 2.2.12 (2016-03-17)
  - Added the appropriate Maven POM resources to be publishing to Bintray's JCenter
  - Refactored the akka-persistence-query interfaces, integrated it back again in one jar, for jcenter deployment simplicity

### 2.2.11 (2016-03-09)
  - Journal and SnapshotDAO implementation are configurable, when you need to implement your own persistency strategy,
  - Enable/Disable Serialization, the default journal and snapshot DAO rely on serialization, only disable when you known what you are doing,
  - Scala 2.11.7 -> 2.11.8

### 2.2.10 (2016-03-04)
  - Fix for parsing the schema name configuration for the `deleted_to` and `snapshot` table configuration.  

### 2.2.9 (2016-03-03)
  - Fix for propagating serialization errors to akka-persistence so that any error regarding the persistence of messages will be handled by the callback handler of the Persistent Actor; `onPersistFailure`.  

### 2.2.8 (2016-02-18)
  - Added InMemory option for journal and snapshot storage, for testing

### 2.2.7 (2016-02-17)
  - Akka 2.4.2-RC3 -> 2.4.2

### 2.2.6 (2016-02-13)
  - akka-persistence-jdbc-query 1.0.0 -> 1.0.1

### 2.2.5 (2016-02-13)
  - Akka 2.4.2-RC2 -> 2.4.2-RC3

### 2.2.4 (2016-02-08)
  - Compatibility with Akka 2.4.2-RC2
  - Refactored the akka-persistence-query extension interfaces to its own jar: `"com.github.dnvriend" %% "akka-persistence-jdbc-query" % "1.0.0"`

### 2.2.3 (2016-01-29)
  - Refactored the akka-persistence-query package structure. It wasn't optimized for use with javadsl. Now the name of the ReadJournal is `JdbcReadJournal` for both Java and Scala, only the package name has been changed to reflect which language it is for.
  - __Scala:__ akka.persistence.jdbc.query.journal.`scaladsl`.JdbcReadJournal
  - __Java:__ akka.persistence.jdbc.query.journal.`javadsl`.JdbcReadJournal

### 2.2.2 (2016-01-28)
  - Support for looking up DataSources using JNDI

### 2.2.1 (2016-01-28)
  - Support for the akka persistence query JavaDSL

### 2.2.0 (2016-01-26)
  - Compatibility with Akka 2.4.2-RC1

### 2.1.2 (2016-01-25)
  - Support for the `currentEventsByPersistenceIdAndTag` and `eventsByPersistenceIdAndTag` queries

### 2.2.1 (2016-01-24)
  - Support for the `eventsByTag` live query
  - Tags are now separated by a character, and not by a tagPrefix
  - Please note the configuration change.

### 2.1.0 (2016-01-24)
 - Support for the `currentEventsByTag` query, the tagged events will be sorted by event sequence number.
 - Table column names are configurable.
 - Schema change for the journal table, added two columns, `tags` and `created`, please update your schema.

### 2.0.4 (2016-01-22)
 - Using the typesafe config for the Slick database configuration,
 - Uses HikariCP as the connection pool,
 - Table names and schema names are configurable,
 - Akka Stream 2.0.1 -> 2.0.1
 - Tested with Oracle XE

### 2.0.3 (2016-01-18)
 - Optimization for the `eventsByPersistenceId` and `allPersistenceIds` queries.

### 2.0.2 (2016-01-17)
 - Support for the `eventsByPersistenceId` live query

### 2.0.1 (2016-01-17)
 - Support for the `allPersistenceIds` live query

### 2.0.0 (2016-01-16)
 - A complete rewrite using [slick][slick] as the database backend, breaking backwards compatibility in a big way.

### 1.2.2 (2015-10-14) - Akka v2.4.x
 - Merged PR #28 [Andrey Kouznetsov][kouznetsov] Removing Unused ExecutionContext, thanks!

### 1.2.1 (2015-10-12)
 - Merged PR #27 [Andrey Kouznetsov][kouznetsov] don't fail on asyncWrite with empty messages, thanks!

### 1.2.0 (2015-10-02)
 - Compatibility with Akka 2.4.0
 - Akka 2.4.0-RC3 -> 2.4.0
 - scalikejdbc 2.2.7 -> 2.2.8
 - No obvious optimalizations are applied, and no schema refactorings are needed (for now)
 - Fully backwards compatible with akka-persistence-jdbc v1.1.8's schema and configuration

### 1.2.0-RC3 (2015-09-17)
 - Compatibility with Akka 2.4.0-RC3
 - No obvious optimalizations are applied, and no schema refactorings are needed (for now)
 - Please note; schema, serialization (strategy) and code refactoring will be iteratively applied on newer release of the 2.4.0-xx branch, but for each step, a migration guide and SQL scripts will be made available.
 - Use the following library dependency: "com.github.dnvriend" %% "akka-persistence-jdbc" % "1.2.0-RC3"
 - Fully backwards compatible with akka-persistence-jdbc v1.1.8's schema and configuration

### 1.2.0-RC2 (2015-09-07)
 - Compatibility with Akka 2.4.0-RC2
 - No obvious optimalizations are applied, and no schema refactorings are needed (for now)
 - Please note; schema, serialization (strategy) and code refactoring will be iteratively applied on newer release of the 2.4.0-xx branch, but for each step, a migration guide and SQL scripts will be made available.
 - Use the following library dependency: "com.github.dnvriend" %% "akka-persistence-jdbc" % "1.2.0-RC2"
 - Fully backwards compatible with akka-persistence-jdbc v1.1.8's schema and configuration

### 1.1.9 (2015-10-12) - Akka v2.3.x
 - scala 2.10.5 -> 2.10.6
 - akka 2.3.13 -> 2.3.14
 - scalikejdbc 2.2.7 -> 2.2.8

### 1.1.8 (2015-09-04)
 - Compatibility with Akka 2.3.13
 - Akka 2.3.12 -> 2.3.13

### 1.1.7 (2015-07-13)
 - Scala 2.11.6 -> 2.11.7
 - Akka 2.3.11 -> 2.3.12
 - ScalaTest 2.1.4 -> 2.2.4

### 1.1.6 (2015-06-22)
 - ScalikeJdbc 2.2.6 -> 2.2.7
 - Issue #22 `persistenceId` missing in `JournalTypeConverter.unmarshal(value: String)` signature; added a second parameter `persistenceId: String`, note this breaks the serialization API.

### 1.1.5 (2015-05-12)
 - Akka 2.3.10 -> 2.3.11
 - MySQL snapshot statement now uses `INSERT INTO .. ON DUPLICATE UPDATE` for `upserts`
 - Merged Issue #21 [mwkohout][mwkohout] Use a ParameterBinder to pass snapshot into the merge statement and get rid of the stored procedure, thanks!

### 1.1.4 (2015-05-06)
  - ScalikeJDBC 2.2.5 -> 2.2.6
  - Akka 2.3.9 -> 2.3.10
  - Switched back to a Java 7 binary, to support Java 7 and higher based projects, we need a strategy though when [Scala 2.12](http://www.scala-lang.org/news/2.12-roadmap) will be released.
  - Merged Issue #20 [mwkohout][mwkohout] Use apache commons codec Base64 vs the java8-only java.util.Base64 for Java 7 based projects, thanks!

### 1.1.3 (2015-04-15)
  - ScalikeJDBC 2.2.4 -> 2.2.5
  - Fixed: 'OutOfMemory error when recovering with a large number of snapshots #17'

### 1.1.2 (2015-03-21)
  - Initial support for a pluggable serialization architecture. Out of the box the plugin uses the
   `Base64JournalConverter` and `Base64SnapshotConverter` as serializers. For more information
   see the [akka-persistence-jdbc-play](https://github.com/dnvriend/akka-persistence-jdbc-play) example
   project that uses its own JSON serialization format to write journal entries to the data store.

### 1.1.1 (2015-03-17)
  - ScalikeJDBC 2.2.2 -> 2.2.4
  - Java 8 binary, so it needs Java 8, you still use Java 6 or 7, upgrade! :P
  - Using the much faster Java8 java.util.Base64 encoder/decoder
  - Bulk insert for journal entries (non-oracle only, sorry)
  - Initial support for JNDI, needs testing though
  - Merged [Paul Roman][roman] Fix typo in journal log message #14, thanks!
  - Merged [Pavel Boldyrev][boldyrev] Fix MS SQL Server support #15 (can not test it though, needs Vagrant), thanks!

### 1.1.0
  - Merged [Pavel Boldyrev](https://github.com/bpg) Fix Oracle SQL `MERGE` statement usage #13 which fixes issue #9 (java.sql.SQLRecoverableException: No more data to read from socket #9), thanks!
  - Change to the Oracle schema, it needs a stored procedure definition.

### 1.0.9 (2015-01-20)
  - ScalikeJDBC 2.1.2 -> 2.2.2
  - Merged [miguel-vila][vila] Adds ´validationQuery´ configuration parameter #10, thanks!
  - Removed Informix support: I just don't have a working Informix docker image (maybe someone can create one and publish it?)

### 1.0.8
  - ScalikeJDBC 2.1.1 -> 2.1.2
  - Moved to bintray

### 1.0.7 (2014-09-16)
  - Merged [mwkohout][mwkohout] fix using Oracle's MERGE on issue #3, thanks!

### 1.0.6
  - Fixed - Issue3: Handling save attempts with duplicate snapshot ids and persistence ids
  - Fixed - Issue5: Connection pool is being redefined when using journal and snapshot store

### 1.0.5 (2014-08-26)
  - Akka 2.3.5 -> 2.3.6
  - ScalikeJDBC 2.1.0 -> 2.1.1

### 1.0.4
  - Added schema name configuration for the journal and snapshot
  - Added table name configuration for the journal and snapshot
  - ScalikeJDBC 2.0.5 -> 2.1.0
  - Akka 2.3.4 -> 2.3.5

### 1.0.3 (2014-07-23)
  - IBM Informix 12.10 supported

### 1.0.2
  - Oracle XE 11g supported

### 1.0.1
  - scalikejdbc 2.0.4 -> 2.0.5
  - akka-persistence-testkit 0.3.3 -> 0.3.4

### 1.0.0 (2014-07-03)
  - Release to Maven Central

### 0.0.6
 - Tested against MySQL/5.6.19 MySQL Community Server (GPL)
 - Tested against H2/1.4.179

### 0.0.5
 - Added the snapshot store

### 0.0.4
 -  Refactored the JdbcSyncWriteJournal so it supports the following databases:

### 0.0.3 (2014-07-01)
 - Using [Martin Krasser's][krasser] [akka-persistence-testkit][ap-testkit]
  to test the akka-persistence-jdbc plugin.
 - Update to Akka 2.3.4

### 0.0.2 (2014-06-30)
 - Using [ScalikeJDBC][scalikejdbc] as the JDBC access library instead of my home-brew one.

### 0.0.1 (2014-05-23)
 - Initial commit

[aenevala]: https://github.com/aenevala
[dispalt]: https://github.com/dispalt
[monktastic]: https://github.com/monktastic
[fcristovao]: https://github.com/fcristovao
[ellawala]: https://github.com/charithe
[turner]: https://github.com/wwwiiilll
[kouznetsov]: https://github.com/prettynatty
[boldyrev]: https://github.com/bpg
[roman]: https://github.com/romusz
[vila]: https://github.com/miguel-vila
[mwkohout]: https://github.com/mwkohout
[krasser]: https://github.com/krasserm
[shah]: https://github.com/gopalsaob
[rockjam]: https://github.com/rockjam
[jtysper]: https://github.com/jtysper
[jroper]: https://github.com/jroper
[skisel]: https://github.com/skisel
[joseblas]: https://github.com/joseblas
[timothyklim]: https://github.com/TimothyKlim
[jiminhsieh]: http://github.com/jiminhsieh
[renatocaval]: http://github.com/renatocaval
[frederic-gendebien]: http://github.com/frederic-gendebien
[sullis]: http://github.com/sullis
[wellingr]: http://github.com/wellingr
[sarahgerweck]: http://github.com/sarahgerweck
[acchaulk]: https://github.com/acchaulk
[dmi3zkm]: https://github.com/dmi3zkm

[scalikejdbc]: http://scalikejdbc.org/
[slick]: http://slick.typesafe.com/
[slick-jndi]: http://slick.typesafe.com/doc/3.1.1/database.html#using-a-jndi-name
[slick-ex]: http://slick.typesafe.com/doc/3.1.1/extensions.html
[slick-ex-lic]: http://slick.typesafe.com/news/2016/02/01/slick-extensions-licensing-change.html

[apache]: http://www.apache.org/licenses/LICENSE-2.0
[w3c-cond]: http://www.w3.org/Consortium/cepc/
[w3c-proc]: http://www.w3.org/Consortium/pwe/#Procedures
[lightbend]: http://www.lightbend.com/

[postgres]: http://www.postgresql.org/
[ap-testkit]: https://github.com/krasserm/akka-persistence-testkit
[ds]: http://docs.oracle.com/javase/8/docs/api/javax/sql/DataSource.html

[ser]: http://doc.akka.io/docs/akka/current/scala/serialization.html
[event-adapter]: http://doc.akka.io/docs/akka/current/scala/persistence.html#event-adapters-scala

[inmemory]: https://github.com/dnvriend/akka-persistence-inmemory
[postgres-application.conf]: https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/postgres-application.conf
[mysql-application.conf]: https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/mysql-application.conf
[h2-application.conf]: https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/h2-application.conf
[oracle-application.conf]: https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/oracle-application.conf

[postgres-schema]: https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/main/resources/schema/postgres/postgres-schema.sql
[mysql-schema]: https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/main/resources/schema/mysql/mysql-schema.sql
[h2-schema]: https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/main/resources/schema/h2/h2-schema.sql
[oracle-schema]: https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/main/resources/schema/oracle/oracle-schema.sql
