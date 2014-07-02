# akka-persistence-jdbc
Akka-persistence-jdbc is a persistence plugin for [akka-persistence](http://doc.akka.io/docs/akka/snapshot/scala/persistence.html) 
that writes journal entries to a configured JDBC database. It supports writing journal messages and snapshots to two tables, 
the journal table and the snapshot table. 

By setting the appropriate Journal and SnapshotStore classes in the application.conf, you can choose the following databases

* Postgresql (default, and the only one tested)
* H2
* MySQL
* Oracle
* MSSqlServer
* DB2

It is currently only tested against Postgresql/9.3.1 and H2/1.4.179

### Todo:

 - Test against more databases, only tested it against PostgreSQL/9.3.1

### Usage
In application.conf place the following:

    akka {
      loglevel = "DEBUG"
    
      persistence {
        journal.plugin = "jdbc-journal"
    
        snapshot-store.plugin = "jdbc-snapshot-store"
    
        # we need event publishing for tests
        publish-confirmations = on
        publish-plugin-commands = on
    
        # disable leveldb (default store impl)
        journal.leveldb.native = off
      }
    
      log-dead-letters = 10
      log-dead-letters-during-shutdown = on
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
    class = "akka.persistence.jdbc.journal.OracleSyncWriteJournal"
    class = "akka.persistence.jdbc.journal.MSSqlServerSyncWriteJournal"
    class = "akka.persistence.jdbc.journal.DB2SyncWriteJournal"

To alter the database dialect of the snapshot-store, change the class to:

      class = "akka.persistence.jdbc.snapshot.PostgresqlSyncSnapshotStore"
      class = "akka.persistence.jdbc.journal.MysqlSyncSnapshotStore"
      class = "akka.persistence.jdbc.journal.H2SyncSnapshotStore"
      class = "akka.persistence.jdbc.journal.OracleSyncSnapshotStore"
      class = "akka.persistence.jdbc.journal.MSSqlServerSyncSnapshotStore"
      class = "akka.persistence.jdbc.journal.DB2SyncSnapshotStore"

### Table schema
The following schema works on Postgresql:

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

### H2 Configuration
The application.conf for H2 should be:

    akka {
      loglevel = "DEBUG"
    
      persistence {
        journal.plugin = "jdbc-journal"
    
        snapshot-store.plugin = "jdbc-snapshot-store"
    
        # we need event publishing for tests
        publish-confirmations = on
        publish-plugin-commands = on
    
        # disable leveldb (default store impl)
        journal.leveldb.native = off
      }
    
      log-dead-letters = 10
      log-dead-letters-during-shutdown = on
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

### 0.0.6
 - Tested against H2/1.4.179

### 0.0.5
 - Added the snapshot store

### 0.0.4
 -  Refactored the JdbcSyncWriteJournal so it supports the following databases:
    - Postgresql  (tested, works on v9.3.1)
    - H2          (tested, works on 1.4.179)
    - MySql       (untested, but should work)
    - MSSqlServer (untested, but should work)
    - Oracle      (untested, but should work) 
    - DB2         (untested, but should work)

### 0.0.3
 - Using [Martin Krasser's](https://github.com/krasserm) [akka-persistence-testkit](https://github.com/krasserm/akka-persistence-testkit)
  to test the akka-persistence-jdbc plugin. 
 - Upgrade to Akka 2.3.4

#### 0.0.2
 - Using [ScalikeJdbc](http://scalikejdbc.org/) as the JDBC access library instead of my home-brew one. 

#### 0.0.1
 - Initial commit