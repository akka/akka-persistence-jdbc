# akka-persistence-jdbc
The akka-persistence-jdbc is a plugin for [akka-persistence](http://doc.akka.io/docs/akka/snapshot/scala/persistence.html) that writes journal entries to a configured JDBC database. It supports writing journal messages and snapshots to two tables:  the `journal` table and the `snapshot` table. 

Master branch | Akka 2.4.0-xx branch | License | Latest Version
------------- | -------------------- | ------- | --------------
[![Build Status: Master](https://travis-ci.org/dnvriend/akka-persistence-jdbc.svg?branch=master)](https://travis-ci.org/dnvriend/akka-persistence-jdbc) | [![Build Status: 2.4.0-xx](https://travis-ci.org/dnvriend/akka-persistence-jdbc.svg?branch=akka-2.4.0-xx)](https://travis-ci.org/dnvriend/akka-persistence-jdbc)  | [![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) | [ ![Download](https://api.bintray.com/packages/dnvriend/maven/akka-persistence-jdbc/images/download.svg) ](https://bintray.com/dnvriend/maven/akka-persistence-jdbc/_latestVersion)

By setting the appropriate Journal and SnapshotStore classes in the application.conf, you can choose the following databases:

* H2           (tested, works on 1.4.179)
* Postgresql   (tested, works on v9.4)
* MySQL        (tested, works on 5.7 MySQL Community Server (GPL))
* Oracle XE    (tested, works on Oracle XE 11g r2)

**Start of Disclaimer:**

> This plugin should not be used in production, ever! For a good, stable and scalable solution use [Apache Cassandra](http://cassandra.apache.org/) with the [akka-persistence-cassandra plugin](https://github.com/krasserm/akka-persistence-cassandra/) Only use this plug-in for study projects and proof of concepts. Please use Docker and [library/cassandra](https://registry.hub.docker.com/u/library/cassandra/) You have been warned! 

**End of Disclaimer**

# Dependency
To include the JDBC plugin into your sbt project, add the following lines to your build.sbt file:

    resolvers += "dnvriend at bintray" at "http://dl.bintray.com/dnvriend/maven"

    libraryDependencies += "com.github.dnvriend" %% "akka-persistence-jdbc" % "1.1.8"

For Maven users, add the following to the pom.xml

    <dependency>
        <groupId>com.github.dnvriend</groupId>
        <artifactId>akka-persistence-jdbc_2.10</artifactId>
        <version>1.1.8</version>
    </dependency>
    
    <dependency>
        <groupId>com.github.dnvriend</groupId>
        <artifactId>akka-persistence-jdbc_2.11</artifactId>
        <version>1.1.8</version>
    </dependency>

Add the following to the repositories section of the pom:

    <repository>
      <snapshots><enabled>false</enabled></snapshots>
      <id>central</id>
      <name>bintray</name>
      <url>http://dl.bintray.com/dnvriend/maven</url>
    </repository>

# What's new?
For the full list of what's new see [this wiki page] (https://github.com/dnvriend/akka-persistence-jdbc/wiki/Version-History).

## 1.2.0-RC2 (2015-09-07) 
 - Compatibility with Akka 2.4.0-RC2
 - No obvious optimalizations are applied, and no schema refactorings are needed (for now)
 - Please note; schema, serialization (strategy) and code refactoring will be iteratively applied on newer release of the 2.4.0-xx branch, but for each step, a migration guide and SQL scripts will be made available.
 - Use the following library dependency: "com.github.dnvriend" %% "akka-persistence-jdbc" % "1.2.0-RC2"
 - Fully backwards compatible with akka-persistence-jdbc v1.1.7's schema and configuration 

## 1.1.8 (2015-09-04)
 - Compatibility with Akka 2.3.13
 - Akka 2.3.12 -> 2.3.13

# Usage
In application.conf place the following:

    akka {
      persistence {
        journal.plugin = "jdbc-journal"
        snapshot-store.plugin = "jdbc-snapshot-store"
      }
    }
    
    jdbc-journal {
      class = "akka.persistence.jdbc.journal.PostgresqlSyncWriteJournal"
    }
    
    jdbc-snapshot-store {
      class = "akka.persistence.jdbc.snapshot.PostgresqlSyncSnapshotStore"
    }
    
    jdbc-connection {
       username ="admin"
       password = "admin"
       driverClassName = "org.postgresql.Driver"
       url = "jdbc:postgresql://localhost:5432/mydb"
       journalSchemaName  = "public"
       journalTableName   = "journal"
       snapshotSchemaName = "public"
       snapshotTableName  = "snapshot"
       jndiPath           = ""
       dataSourceName     = ""
       journal-converter  = "akka.persistence.jdbc.serialization.journal.Base64JournalConverter"
       snapshot-converter = "akka.persistence.jdbc.serialization.snapshot.Base64SnapshotConverter"
    }

__Note:__
You can drop the schema name (username/database name) part of the query by setting the
schema names to "", the query that runs will not have the <schemaName>.<tableName> format 
but only the tablename, like so:

    jdbc-connection {
     journalSchemaName  = ""
     snapshotSchemaName = ""
    }

__validationQuery:__
You can add the validationQuery key to the jdbc-connection so the connection pool will execute this query from time to time
so the database connection will not time out when not in use. The example below can be used with the Oracle database. When
you do not use the validationQuery, please remove the key:

    jdbc-connection {
     validationQuery = "select 1 from dual"
    }

The jdbc-journal and jdbc-snapshot-store sections are optional. The defaults are the PostgresqlSyncWriteJournal and the
PostgresqlSyncSnapshotStore. 

To alter the database dialect for the journal, change the class to:

    class = "akka.persistence.jdbc.journal.PostgresqlSyncWriteJournal"
    class = "akka.persistence.jdbc.journal.MysqlSyncWriteJournal"
    class = "akka.persistence.jdbc.journal.H2SyncWriteJournal"
    class = "akka.persistence.jdbc.journal.OracleSyncWriteJournal"

To alter the database dialect of the snapshot-store, change the class to:

      class = "akka.persistence.jdbc.snapshot.PostgresqlSyncSnapshotStore"
      class = "akka.persistence.jdbc.snapshot.MysqlSyncSnapshotStore"
      class = "akka.persistence.jdbc.snapshot.H2SyncSnapshotStore"
      class = "akka.persistence.jdbc.snapshot.OracleSyncSnapshotStore"

__JNDI:__

[ScalikeJDBC has support for JNDI connections](http://scalikejdbc.org/documentation/configuration.html), and using that
example it should be as easy as adding the jndiPath to the configuration with the dataSourceName, and off you go:

    jdbc-connection {
      journalSchemaName  = "public"
      journalTableName   = "journal"
      snapshotSchemaName = "public"
      snapshotTableName  = "snapshot"
      jndiPath           = ""
      dataSourceName     = ""
    }

__Custom Journal and Event serialization__
akka-persistence has support for your own journal and snapshot formats. Out of the box it comes with an implementation
that serializes to Base64. Just extend the `JournalTypeConverter` and `SnapshotTypeConverter`, and register your 
serializer in the application.conf, and your off serializing with your own format. For more information please see
the [example play application](https://github.com/dnvriend/akka-persistence-jdbc-play) that uses JSON to serialize 
the payload to the database. 

    jdbc-connection {
       journal-converter  = "akka.persistence.jdbc.serialization.journal.Base64JournalConverter"
       snapshot-converter = "akka.persistence.jdbc.serialization.snapshot.Base64SnapshotConverter"
    }
