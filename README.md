# akka-persistence-jdbc
A JDBC plugin for [akka-persistence](http://akka.io), a great extension to Akka created by [Martin Krasser](https://github.com/krasserm)
It is only tested against Postgresql/9.3.1, so when you have any problems with other databases, drop me a mail and maybe
we can work something out.

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

### What's new?

### 0.0.5
 - Added the snapshot store

### 0.0.4
 -  Refactored the JdbcSyncWriteJournal so it supports the following databases:
    - Postgresql  (tested, works on v9.3.1)
    - MySql       (untested, but should work)
    - H2          (untested, but should work) 
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