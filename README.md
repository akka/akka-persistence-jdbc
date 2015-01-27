# akka-persistence-jdbc
Akka-persistence-jdbc is a plugin for [akka-persistence](http://doc.akka.io/docs/akka/snapshot/scala/persistence.html) 
that writes journal entries to a configured JDBC database. It supports writing journal messages and snapshots to two tables: 
the 'journal' table and the 'snapshot' table. 

By setting the appropriate Journal and SnapshotStore classes in the application.conf, you can choose the following databases

* H2           (tested, works on 1.4.179)
* Postgresql   (tested, works on v9.3.1)
* MySql        (tested, works on 5.6.19 MySQL Community Server (GPL))
* Oracle XE    (tested, works on Oracle XE 11g r2)

Note: The plugin should work on other databases too. When you wish to have support for a database, contact me and we can work
something out.

# Dependency
To include the JDBC plugin into your sbt project, add the following lines to your build.sbt file:

    resolvers += "dnvriend at bintray" at "http://dl.bintray.com/dnvriend/maven"

    libraryDependencies += "com.github.dnvriend" %% "akka-persistence-jdbc" % "1.1.0"

For Maven users, add the following to the pom.xml

    <dependency>
        <groupId>com.github.dnvriend</groupId>
        <artifactId>akka-persistence-jdbc_2.10</artifactId>
        <version>1.1.0</version>
    </dependency>
    
    <dependency>
        <groupId>com.github.dnvriend</groupId>
        <artifactId>akka-persistence-jdbc_2.11</artifactId>
        <version>1.1.0</version>
    </dependency>

Add the following to the repositories section of the pom:

    <repository>
      <snapshots><enabled>false</enabled></snapshots>
      <id>central</id>
      <name>bintray</name>
      <url>http://dl.bintray.com/dnvriend/maven</url>
    </repository>

## What's new?

### 1.1.0
 - Merged [Pavel Boldyrev](https://github.com/bpg) Fix Oracle SQL `MERGE` statement usage #13 which fixes issue #9 (java.sql.SQLRecoverableException: No more data to read from socket #9), thanks!
 - Change to the Oracle schema, it needs a stored procedure definition.

### 1.0.9
 - ScalikeJDBC 2.1.2 -> 2.2.2
 - Merged [miguel-vila](https://github.com/miguel-vila) Adds ´validationQuery´ configuration parameter #10, thanks!
 - Removed Informix support: I just don't have a working Informix docker image (maybe someone can create one and publish it?)

### 1.0.8
 - ScalikeJDBC 2.1.1 -> 2.1.2
 - Moved to bintray

### 1.0.7
 - Merged [mwkohout](https://github.com/mwkohout) fix using Oracle's MERGE on issue #3, thanks! 

### 1.0.6
 - Fixed - Issue3: Handling save attempts with duplicate snapshot ids and persistence ids
 - Fixed - Issue5: Connection pool is being redefined when using journal and snapshot store

### 1.0.5
 - Akka 2.3.5 -> 2.3.6
 - ScalikeJDBC 2.1.0 -> 2.1.1

### 1.0.4
 - Added schema name configuration for the journal and snapshot
 - Added table name configuration for the journal and snapshot
 - ScalikeJDBC 2.0.5 -> 2.1.0 
 - Akka 2.3.4 -> 2.3.5 

### 1.0.3
 - IBM Informix 12.10 supported 

### 1.0.2
 - Oracle XE 11g supported

### 1.0.1
 - scalikejdbc 2.0.4 -> 2.0.5
 - akka-persistence-testkit 0.3.3 -> 0.3.4

### 1.0.0
 - Release to Maven Central

### 0.0.6
 - Tested against MySQL/5.6.19 MySQL Community Server (GPL) 
 - Tested against H2/1.4.179

### 0.0.5
 - Added the snapshot store

### 0.0.4
 -  Refactored the JdbcSyncWriteJournal so it supports the following databases:

### 0.0.3
 - Using [Martin Krasser's](https://github.com/krasserm) [akka-persistence-testkit](https://github.com/krasserm/akka-persistence-testkit)
  to test the akka-persistence-jdbc plugin. 
 - Update to Akka 2.3.4

#### 0.0.2
 - Using [ScalikeJDBC](http://scalikejdbc.org/) as the JDBC access library instead of my home-brew one. 

#### 0.0.1
 - Initial commit

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
    snapshotSchemaName = "public"
    snapshotTableName  = ""

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
    
# Testing
 - Install [boot2docker](http://boot2docker.io/) for your operating system (I use OSX)
 - Add the shell integration configuration
 - Execute the appropriate test shell script
