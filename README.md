# akka-persistence-jdbc
Akka-persistence-jdbc is a plugin for [akka-persistence](http://doc.akka.io/docs/akka/snapshot/scala/persistence.html) 
that writes journal entries to a configured JDBC database. It supports writing journal messages and snapshots to two tables: 
the 'journal' table and the 'snapshot' table. 

By setting the appropriate Journal and SnapshotStore classes in the application.conf, you can choose the following databases

* Postgresql  (tested, works on v9.3.1)
* MySql       (tested, works on 5.6.19 MySQL Community Server (GPL))
* H2          (tested, works on 1.4.179)
* Oracle XE   (tested, works on Oracle XE 11g r2)

Note: The plugin should work on other databases too. When you wish to have support for a database, contact me and we can work
something out.

# Installation
To include the JDBC plugin into your sbt project, add the following lines to your build.sbt file:

    libraryDependencies += "com.github.dnvriend" %% "akka-persistence-jdbc" % "1.0.2"

For Maven users, add the following to the pom.xml

    <dependency>
        <groupId>com.github.dnvriend</groupId>
        <artifactId>akka-persistence-jdbc_2.10</artifactId>
        <version>1.0.2</version>
    </dependency>
    
    <dependency>
        <groupId>com.github.dnvriend</groupId>
        <artifactId>akka-persistence-jdbc_2.11</artifactId>
        <version>1.0.2</version>
    </dependency>

This version of akka-persistence-jdbc depends on Akka 2.3.4 and is cross-built against Scala 2.10.4 and 2.11.0

### What's new?

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
 - Upgrade to Akka 2.3.4

#### 0.0.2
 - Using [ScalikeJdbc](http://scalikejdbc.org/) as the JDBC access library instead of my home-brew one. 

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
## Postgresql and H2
The following schema works on both Postgresql and H2

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

## Oracle
The following schema works on Oracle
    
    CREATE TABLE journal (
      persistence_id VARCHAR(255) NOT NULL,
      sequence_number NUMERIC NOT NULL,
      marker VARCHAR(255) NOT NULL,
      message CLOB NOT NULL,
      created TIMESTAMP NOT NULL,
      PRIMARY KEY(persistence_id, sequence_number)
    );
        
    CREATE TABLE snapshot (
      persistence_id VARCHAR(255) NOT NULL,
      sequence_nr NUMERIC NOT NULL,
      snapshot CLOB NOT NULL,
      created NUMERIC NOT NULL,
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
        }

# Testing
## PostgreSQL
For more information about the Postgres docker image, please view [training/postgres](https://registry.hub.docker.com/u/training/postgres/)

    sudo docker run --name postgres -d -p 49432:5432 training/postgres
    sudo docker run -ti -v /akka-persistence-jdbc:/akka-persistence-jdbc --link postgres:postgres --name test murad/java8 /bin/bash 

Connect database with following setting and create the database schema:  

    hostname: 192.168.99.99 
    port: 49432    
    username: docker 
    password: docker
    database: docker

The test application.conf should be:

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
    
    jdbc-connection {
      username ="docker"
      password = "docker"
      driverClassName = "org.postgresql.Driver"
      url = "jdbc:postgresql://"${DB_PORT_5432_TCP_ADDR}":"${DB_PORT_5432_TCP_PORT}"/docker"
    }
    
In the container named 'test', type:

    cd /akka-persistence-jdbc
    ./activator "test-only akka.persistence.jdbc.journal.PostgresqlSyncJournalSpec"
    ./activator "test-only akka.persistence.jdbc.snapshot.PostgresqlSyncSnapshotStoreSpec"

## MySQL
For more information about the MySQL docker image, please view [mysql](https://registry.hub.docker.com/_/mysql/)

    sudo docker run --name mysql -e MYSQL_ROOT_PASSWORD=root -d -p 49306:3306 mysql
    sudo docker run -ti -v /akka-persistence-jdbc:/akka-persistence-jdbc --link mysql:mysql --name test murad/java8 /bin/bash 

Connect database with following setting and create the database schema:  

    hostname: 192.168.99.99 
    port: 49306    
    username: root 
    password: root
    database: mysql

The test application.conf should be:

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
      class = "akka.persistence.jdbc.journal.MysqlSyncWriteJournal"
    }
    
    jdbc-snapshot-store {
      class = "akka.persistence.jdbc.snapshot.MysqlSyncSnapshotStore"
    }
    
    jdbc-connection {
       username ="root"
       password = "root"
       driverClassName = "com.mysql.jdbc.Driver"
       url = "jdbc:mysql://"${MYSQL_PORT_3306_TCP_ADDR}":"${MYSQL_PORT_3306_TCP_PORT}"/mysql"
    }   
    
In the container named 'test', type:

    cd /akka-persistence-jdbc
    ./activator "test-only akka.persistence.jdbc.journal.MysqlSyncJournalSpec"
    ./activator "test-only akka.persistence.jdbc.snapshot.MysqlSyncSnapshotStoreSpec"

## Oracle
For more information about the Oracle XE docker image, please view [alexeiled / docker-oracle-xe-11g](https://registry.hub.docker.com/u/alexeiled/docker-oracle-xe-11g/)

    sudo docker run -d --name oracle -p 49160:22 -p 49161:1521 -p 49162:8080 alexeiled/docker-oracle-xe-11g
    sudo docker run -ti -v /akka-persistence-jdbc:/akka-persistence-jdbc --link oracle:oracle --name test murad/java8 /bin/bash 

Connect database with following setting and create the database schema:  

    hostname: 192.168.99.99 
    port: 49161
    sid: xe 
    username: system 
    password: oracle 

Password for SYS  

    oracle 

Connect to Oracle Application Express web management console with following settings:  

    url: http://localhost:49162/apex 
    workspace: INTERNAL 
    user: ADMIN 
    password: oracle 

Login by SSH  

    ssh root@localhost -p 49160 
    password: admin
    
The test application.conf should be:

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
      class = "akka.persistence.jdbc.journal.OracleSyncWriteJournal"
    }
    
    jdbc-snapshot-store {
      class = "akka.persistence.jdbc.snapshot.OracleSyncSnapshotStore"
    }
    
    jdbc-connection {
       username ="system"
       password = "oracle"
       driverClassName = "oracle.jdbc.OracleDriver"
       url = "jdbc:oracle:thin:@//"${ORACLE_PORT_1521_TCP_ADDR}":"${ORACLE_PORT_1521_TCP_PORT}"/xe"
    }
    
In the container named 'test', type:

    cd /akka-persistence-jdbc
    ./activator "test-only akka.persistence.jdbc.journal.OracleSyncJournalSpec"
    ./activator "test-only akka.persistence.jdbc.snapshot.OracleSyncSnapshotStoreSpec"