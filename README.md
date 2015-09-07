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

__JNDI (UNTESTED):__

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

# Table schema
## H2
The following schema works on H2
    
    CREATE TABLE IF NOT EXISTS public.journal (
      persistence_id VARCHAR(255) NOT NULL,
      sequence_number BIGINT NOT NULL,
      marker VARCHAR(255) NOT NULL,
      message TEXT NOT NULL,
      created TIMESTAMP NOT NULL,
      PRIMARY KEY(persistence_id, sequence_number)
    );
        
    CREATE TABLE IF NOT EXISTS public.snapshot (
      persistence_id VARCHAR(255) NOT NULL,
      sequence_nr BIGINT NOT NULL,
      snapshot TEXT NOT NULL,
      created BIGINT NOT NULL,
      PRIMARY KEY (persistence_id, sequence_nr)
    );

## Postgresql 
The following schema works on Postgresql

    CREATE TABLE IF NOT EXISTS public.journal (
      persistence_id VARCHAR(255) NOT NULL,
      sequence_number BIGINT NOT NULL,
      marker VARCHAR(255) NOT NULL,
      message TEXT NOT NULL,
      created TIMESTAMP NOT NULL,
      PRIMARY KEY(persistence_id, sequence_number)
    );
    
    
    CREATE TABLE IF NOT EXISTS public.snapshot (
      persistence_id VARCHAR(255) NOT NULL,
      sequence_nr BIGINT NOT NULL,
      snapshot TEXT NOT NULL,
      created BIGINT NOT NULL,
      PRIMARY KEY (persistence_id, sequence_nr)
    );

## MS SQL Server
The following schema works on MS SQL Server

    IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.journal') AND type in (N'U'))
    CREATE TABLE dbo.journal (
      persistence_id  VARCHAR(255)  NOT NULL,
      sequence_number BIGINT        NOT NULL,
      marker          VARCHAR(255)  NOT NULL,
      message         NVARCHAR(MAX) NOT NULL,
      created         DATETIME      NOT NULL,
      PRIMARY KEY (persistence_id, sequence_number)
    )
    GO

    IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.snapshot') AND type in (N'U'))
    CREATE TABLE dbo.snapshot (
      persistence_id VARCHAR(255)  NOT NULL,
      sequence_nr    BIGINT        NOT NULL,
      snapshot       NVARCHAR(MAX) NOT NULL,
      created        BIGINT        NOT NULL,
      PRIMARY KEY (persistence_id, sequence_nr)
    )
    GO

__Note:__
Please set the schema names to "dbo" like so:

    journalSchemaName  = "dbo"
    journalTableName   = "journal"
    snapshotSchemaName = "dbo"
    snapshotTableName  = "snapshot"


## MySQL
The following schema works on MySQL

    CREATE TABLE IF NOT EXISTS journal (
      persistence_id VARCHAR(255) NOT NULL,
      sequence_number BIGINT NOT NULL,
      marker VARCHAR(255) NOT NULL,
      message TEXT NOT NULL,
      created TIMESTAMP NOT NULL,
      PRIMARY KEY(persistence_id, sequence_number)
    );
    
    CREATE TABLE IF NOT EXISTS snapshot (
      persistence_id VARCHAR(255) NOT NULL,
      sequence_nr BIGINT NOT NULL,
      snapshot TEXT NOT NULL,
      created BIGINT NOT NULL,
      PRIMARY KEY (persistence_id, sequence_nr)
    );

__Note:__ 
Please set the schema names to "" like so:

    journalSchemaName  = ""
    journalTableName   = "journal"
    snapshotSchemaName = ""
    snapshotTableName  = "snapshot"

When you wish to use the schemaName for the database name, then fill the
schema names to be the database name

## Oracle
The following schema works on Oracle
    
    CREATE TABLE public.journal (
      persistence_id VARCHAR(255) NOT NULL,
      sequence_number NUMERIC NOT NULL,
      marker VARCHAR(255) NOT NULL,
      message CLOB NOT NULL,
      created TIMESTAMP NOT NULL,
      PRIMARY KEY(persistence_id, sequence_number)
    );
        
    CREATE TABLE public.snapshot (
      persistence_id VARCHAR(255) NOT NULL,
      sequence_nr NUMERIC NOT NULL,
      snapshot CLOB NOT NULL,
      created NUMERIC NOT NULL,
      PRIMARY KEY (persistence_id, sequence_nr)
    );
    
    CREATE OR REPLACE PROCEDURE sp_save_snapshot(
      p_persistence_id IN VARCHAR,
      p_sequence_nr    IN NUMERIC,
      p_snapshot       IN CLOB,
      p_created        IN NUMERIC
    ) IS

      BEGIN
        INSERT INTO public.snapshot (persistence_id, sequence_nr, snapshot, created)
        VALUES
          (p_persistence_id, p_sequence_nr, p_snapshot, p_created);
        EXCEPTION
        WHEN DUP_VAL_ON_INDEX THEN
        UPDATE public.snapshot
        SET snapshot = p_snapshot, created = p_created
        WHERE persistence_id = p_persistence_id AND sequence_nr = p_sequence_nr;
      END;

__Note1:__
Name of the "snapshots" table is also specified in `sp_save_snapshot` body. If you change table names don't forget to update SP as well.

__Note2:__
Oracle schemas are users really. To use a username for the schema name, 
set the configuration like:

    journalSchemaName  = "system"
    journalTableName   = "journal"
    snapshotSchemaName = "system"
    snapshotTableName  = "snapshot"

To not use schema names, set the configuration to:

    journalSchemaName  = ""
    journalTableName   = "journal"
    snapshotSchemaName = ""
    snapshotTableName  = "snapshot"

# Postgresql Configuration
The application.conf for Postgresql should be:


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
    }    

# MS SQL Server Configuration
The application.conf for MS SQL Server should be:

    akka {
      persistence {
        journal.plugin = "jdbc-journal"
        snapshot-store.plugin = "jdbc-snapshot-store"
      }
    }

    jdbc-journal {
      class = "akka.persistence.jdbc.journal.MSSqlServerSyncWriteJournal"
    }

    jdbc-snapshot-store {
      class = "akka.persistence.jdbc.snapshot.MSSqlServerSyncSnapshotStore"
    }

    mssql {
      host = "localhost"
      port = "1433"
      database = "master"
    }

    jdbc-connection {
      username           = "sa"
      password           = "password"
      driverClassName    = "com.microsoft.sqlserver.jdbc.SQLServerDriver"
      url                = "jdbc:sqlserver://"${mssql.host}":"${mssql.port}";databaseName=${mssql.database}"
      journalSchemaName  = "dbo"
      journalTableName   = "journal"
      snapshotSchemaName = "dbo"
      snapshotTableName  = "snapshot"
      validationQuery    = "select 1"
    }

# MySQL Configuration
The application.conf for MySQL should be:

    akka {
      persistence {
        journal.plugin = "jdbc-journal"
        snapshot-store.plugin = "jdbc-snapshot-store"
      }
    }
    
    jdbc-journal {
      class = "akka.persistence.jdbc.journal.MysqlSyncWriteJournal"
    }
    
    jdbc-snapshot-store {
      class = "akka.persistence.jdbc.snapshot.MysqlSyncSnapshotStore"
    }
    
    jdbc-connection {
       username ="admin"
       password = "admin"
       driverClassName = "com.mysql.jdbc.Driver"
       url = "jdbc:mysql://localhost/test"
       journalSchemaName  = ""
       journalTableName   = "journal"
       snapshotSchemaName = ""
       snapshotTableName  = "snapshot"
    }    

# H2 Configuration
The application.conf for H2 should be:
    
    akka {
      persistence {
        journal.plugin = "jdbc-journal"
        snapshot-store.plugin = "jdbc-snapshot-store"
      }
    }
    
    jdbc-journal {
      class = "akka.persistence.jdbc.journal.H2SyncWriteJournal"
    }
    
    jdbc-snapshot-store {
      class = "akka.persistence.jdbc.snapshot.H2SyncSnapshotStore"
    }
    
    jdbc-connection {
       username ="sa"
       password = ""
       driverClassName = "org.h2.Driver"
       url = "jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
       journalSchemaName  = "public"
       journalTableName   = "journal"
       snapshotSchemaName = "public"
       snapshotTableName  = "snapshot"
    }
    
# Oracle Configuration
The application.conf for Oracle should be:
    
    akka {
      persistence {
        journal.plugin = "jdbc-journal"
        snapshot-store.plugin = "jdbc-snapshot-store"
      }
    }
    
    jdbc-journal {
      class = "akka.persistence.jdbc.journal.OracleSyncWriteJournal"
    }
    
    jdbc-snapshot-store {
      class = "akka.persistence.jdbc.snapshot.OracleSyncSnapshotStore"
    }
    
    jdbc-connection {
        username ="system"
        password = "oracle"
        driverClassName = "oracle.jdbc.OracleDriver"
        url = "jdbc:oracle:thin:@//192.168.99.99:49161/xe"
        journalSchemaName  = "system"
        journalTableName   = "journal"
        snapshotSchemaName = "system"
        snapshotTableName  = "snapshot"
    }
