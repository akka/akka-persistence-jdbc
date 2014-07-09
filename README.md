# akka-persistence-jdbc
Akka-persistence-jdbc is a plugin for [akka-persistence](http://doc.akka.io/docs/akka/snapshot/scala/persistence.html) 
that writes journal entries to a configured JDBC database. It supports writing journal messages and snapshots to two tables: 
the 'journal' table and the 'snapshot' table. 

By setting the appropriate Journal and SnapshotStore classes in the application.conf, you can choose the following databases

* Postgresql  (tested, works on v9.3.1)
* MySql       (tested, works on 5.6.19 MySQL Community Server (GPL))
* H2          (tested, works on 1.4.179)

Note: The plugin should work on other databases too. When you wish to have support for a database, contact me and we can work
something out.

# Installation
To include the JDBC plugin into your sbt project, add the following lines to your build.sbt file:

    libraryDependencies += "com.github.dnvriend" %% "akka-persistence-jdbc" % "1.0.0"

For Maven users, add the following to the pom.xml

    <dependency>
        <groupId>com.github.dnvriend</groupId>
        <artifactId>akka-persistence-jdbc_2.10</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <dependency>
        <groupId>com.github.dnvriend</groupId>
        <artifactId>akka-persistence-jdbc_2.11</artifactId>
        <version>1.0.0</version>
    </dependency>

This version of akka-persistence-jdbc depends on Akka 2.3.4 and is cross-built against Scala 2.10.4 and 2.11.0

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
    }

The jdbc-journal and jdbc-snapshot-store sections are optional. The defaults are the PostgresqlSyncWriteJournal and the
PostgresqlSyncSnapshotStore. 

To alter the database dialect for the journal, change the class to:

    class = "akka.persistence.jdbc.journal.PostgresqlSyncWriteJournal"
    class = "akka.persistence.jdbc.journal.MysqlSyncWriteJournal"
    class = "akka.persistence.jdbc.journal.H2SyncWriteJournal"

To alter the database dialect of the snapshot-store, change the class to:

      class = "akka.persistence.jdbc.snapshot.PostgresqlSyncSnapshotStore"
      class = "akka.persistence.jdbc.snapshot.MysqlSyncSnapshotStore"
      class = "akka.persistence.jdbc.snapshot.H2SyncSnapshotStore"

# Table schema
The following schema works on Postgresql and H2:

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
    }
    
### What's new?

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
 - Upgrade to Akka 2.3.4

#### 0.0.2
 - Using [ScalikeJdbc](http://scalikejdbc.org/) as the JDBC access library instead of my home-brew one. 

#### 0.0.1
 - Initial commit
