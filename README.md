# akka-persistence-jdbc
A JDBC plugin for [akka-persistence](http://akka.io), a great plugin for Akka created by [Martin Krasser's](https://github.com/krasserm)

 - Note: this is a work in progress, please don't use in production!!

### Todo:

 - Snapshotting; no support for it now, only the Journal
 - Only tested it against PostgreSQL/9.3.1

### What's new?

### 0.0.4
 -  Refactored the JdbcSyncWriteJournal so it supports the following databases:
    - Postgresql  (tested, works on v9.3.1)
    - MySql       (untested, but should work)
    - H2          (untested, but should work) 
    - MSSqlServer (untested, but should work)
    - Oracle      (untested, but should work) 
    - DB2         (untested, but should work)
    
#### Usage
In application.conf place the following:

    akka {
      loglevel = "DEBUG"
    
      persistence {
        journal.plugin = "jdbc-journal"
                
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
    
    jdbc-connection {
       username ="admin"
       password = "admin"
       driverClassName = "org.postgresql.Driver"
       url = "jdbc:postgresql://localhost:5432/mydb"
    }

The jdbc-journal section is optional. It defaults to the PostgresqlSyncWriteJournal. You can replace the class with
the following when you need the JdbcSyncWriteJournal to use other SQL dialects:

    class = "akka.persistence.jdbc.journal.PostgresqlSyncWriteJournal"
    class = "akka.persistence.jdbc.journal.MysqlSyncWriteJournal"
    class = "akka.persistence.jdbc.journal.H2SyncWriteJournal"
    class = "akka.persistence.jdbc.journal.OracleSyncWriteJournal"
    class = "akka.persistence.jdbc.journal.MSSqlServerSyncWriteJournal"
    class = "akka.persistence.jdbc.journal.DB2SyncWriteJournal"

### 0.0.3
 - Using [Martin Krasser's](https://github.com/krasserm) [akka-persistence-testkit](https://github.com/krasserm/akka-persistence-testkit)
  to test the akka-persistence-jdbc plugin. 
 - Upgrade to Akka 2.3.4

#### 0.0.2
 - Using [ScalikeJdbc](http://scalikejdbc.org/) as the JDBC access library instead of my home-brew one. 

#### 0.0.1
 - Initial commit