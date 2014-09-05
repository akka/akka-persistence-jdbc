# akka-persistence-jdbc
Akka-persistence-jdbc is a plugin for [akka-persistence](http://doc.akka.io/docs/akka/snapshot/scala/persistence.html) 
that writes journal entries to a configured JDBC database. It supports writing journal messages and snapshots to two tables: 
the 'journal' table and the 'snapshot' table. 

By setting the appropriate Journal and SnapshotStore classes in the application.conf, you can choose the following databases

* H2           (tested, works on 1.4.179)
* Postgresql   (tested, works on v9.3.1)
* MySql        (tested, works on 5.6.19 MySQL Community Server (GPL))
* Oracle XE    (tested, works on Oracle XE 11g r2)
* IBM Informix (tested, works on v12.10)

Note: The plugin should work on other databases too. When you wish to have support for a database, contact me and we can work
something out.

# Installation
To include the JDBC plugin into your sbt project, add the following lines to your build.sbt file:

    libraryDependencies += "com.github.dnvriend" %% "akka-persistence-jdbc" % "1.0.5"

For Maven users, add the following to the pom.xml

    <dependency>
        <groupId>com.github.dnvriend</groupId>
        <artifactId>akka-persistence-jdbc_2.10</artifactId>
        <version>1.0.5</version>
    </dependency>
    
    <dependency>
        <groupId>com.github.dnvriend</groupId>
        <artifactId>akka-persistence-jdbc_2.11</artifactId>
        <version>1.0.5</version>
    </dependency>

This version of akka-persistence-jdbc depends on Akka 2.3.4 and is cross-built against Scala 2.10.4 and 2.11.1

## What's new?

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

    journalSchemaName  = ""
    snapshotSchemaName = ""

The jdbc-journal and jdbc-snapshot-store sections are optional. The defaults are the PostgresqlSyncWriteJournal and the
PostgresqlSyncSnapshotStore. 

To alter the database dialect for the journal, change the class to:

    class = "akka.persistence.jdbc.journal.PostgresqlSyncWriteJournal"
    class = "akka.persistence.jdbc.journal.MysqlSyncWriteJournal"
    class = "akka.persistence.jdbc.journal.H2SyncWriteJournal"
    class = "akka.persistence.jdbc.journal.OracleSyncWriteJournal"
    class = "akka.persistence.jdbc.journal.InformixSyncWriteJournal"

To alter the database dialect of the snapshot-store, change the class to:

      class = "akka.persistence.jdbc.snapshot.PostgresqlSyncSnapshotStore"
      class = "akka.persistence.jdbc.snapshot.MysqlSyncSnapshotStore"
      class = "akka.persistence.jdbc.snapshot.H2SyncSnapshotStore"
      class = "akka.persistence.jdbc.snapshot.OracleSyncSnapshotStore"
      class = "akka.persistence.jdbc.snapshot.InformixSyncSnapshotStore"

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

__Note:__
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

# IBM Informix
The following schema works on Informix 12.10

    CREATE TABLE IF NOT EXISTS journal (
      persistence_id VARCHAR(255) NOT NULL,
      sequence_number NUMERIC NOT NULL,
      marker VARCHAR(255) NOT NULL,
      message CLOB NOT NULL,
      created DATETIME YEAR TO FRACTION(5) NOT NULL,
      PRIMARY KEY(persistence_id, sequence_number)
    );
    
    CREATE TABLE IF NOT EXISTS snapshot (
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

# IBM Informix configuration
The application.conf for Informix should be:

    akka {
      persistence {
        journal.plugin = "jdbc-journal"
        snapshot-store.plugin = "jdbc-snapshot-store"
      }
    }
    
    jdbc-journal {
      class = "akka.persistence.jdbc.journal.InformixSyncWriteJournal"
    }
    
    jdbc-snapshot-store {
      class = "akka.persistence.jdbc.snapshot.InformixSyncSnapshotStore"
    }
        
    jdbc-connection {
      username ="informix"
      password = "informix"
      driverClassName = "com.informix.jdbc.IfxDriver"
      url = "jdbc:informix-sqli://192.168.99.99:9088/test:INFORMIXSERVER=ol_cyklo"
      journalSchemaName  = "public"
      journalTableName   = "journal"
      snapshotSchemaName = "public"
      snapshotTableName  = "snapshot"
    }
    
# Testing
## PostgreSQL
For more information about the Postgres docker image, please view [training/postgres](https://registry.hub.docker.com/u/training/postgres/)

    sudo docker run --name postgres -d -p 49432:5432 training/postgres
    sudo docker run -ti -v /akka-persistence-jdbc:/akka-persistence-jdbc --link postgres:postgres --name test murad/java8 /bin/bash 

Connect to the database with following settings and create the database schema:  

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
        url = "jdbc:postgresql://"${POSTGRES_PORT_5432_TCP_ADDR}":"${POSTGRES_PORT_5432_TCP_PORT}"/docker"
        journalSchemaName  = "public"
        journalTableName   = "journal"
        snapshotSchemaName = "public"
        snapshotTableName  = "snapshot"
    }


In the container named 'test', type:

    cd /akka-persistence-jdbc
    ./activator "test-only akka.persistence.jdbc.journal.PostgresqlSyncJournalSpec"
    ./activator "test-only akka.persistence.jdbc.snapshot.PostgresqlSyncSnapshotStoreSpec"

## H2
For more information about the H2 docker image, please view [viliusl / ubuntu-h2-server](https://registry.hub.docker.com/u/viliusl/ubuntu-h2-server/)

    sudo docker run --name h2 -d -p 1522:1521 -P viliusl/ubuntu-h2-server
    sudo docker run -ti -v /akka-persistence-jdbc:/akka-persistence-jdbc --link h2:h2 --name test murad/java8 /bin/bash

Connect to the database with the following settings:

    hostname: 192.168.99.99 
    port: 1522    
    username: sa 
    password: 
    database: test

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
      class = "akka.persistence.jdbc.journal.H2SyncWriteJournal"
    }
    
    jdbc-snapshot-store {
      class = "akka.persistence.jdbc.snapshot.H2SyncSnapshotStore"
    }
    
    h2 {
      host = "192.168.99.99"
      host = ${?H2_PORT_1521_TCP_ADDR}
      port = "1522"
      port = ${?H2_PORT_1521_TCP_PORT}
    }
    
    jdbc-connection {
      username ="sa"
      password = ""
      driverClassName = "org.h2.Driver"
      url = "jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
    //  url = "jdbc:h2:tcp://"${h2.host}":"${h2.port}"/test"
      journalSchemaName  = "public"
      journalTableName   = "journal"
      snapshotSchemaName = "public"
      snapshotTableName  = "snapshot"
    }

In the container named 'test', type:

    cd /akka-persistence-jdbc
    ./activator "test-only akka.persistence.jdbc.journal.H2SyncJournalSpec"
    ./activator "test-only akka.persistence.jdbc.snapshot.H2SyncSnapshotStoreSpec"

## MySQL
For more information about the MySQL docker image, please view [mysql](https://registry.hub.docker.com/_/mysql/)

    sudo docker run --name mysql -e MYSQL_ROOT_PASSWORD=root -d -p 49306:3306 mysql
    sudo docker run -ti -v /akka-persistence-jdbc:/akka-persistence-jdbc --link mysql:mysql --name test murad/java8 /bin/bash 

Connect to the database with following settings and create the database schema:  

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
       journalSchemaName  = ""
       journalTableName   = "journal"
       snapshotSchemaName = ""
       snapshotTableName  = "snapshot"
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
       journalSchemaName  = "system"
       journalTableName   = "journal"
       snapshotSchemaName = "system"
       snapshotTableName  = "snapshot"
    } 
    
In the container named 'test', type:

    cd /akka-persistence-jdbc
    ./activator "test-only akka.persistence.jdbc.journal.OracleSyncJournalSpec"
    ./activator "test-only akka.persistence.jdbc.snapshot.OracleSyncSnapshotStoreSpec"

## IBM Informix
IBM does not yet have docker images for there products, so I have created one.

    sudo docker run -ti --name informix -p 9088:9088 dnvriend/ibm-informix /bin/bash
    
In the informix container run:

    su informix
    oninit -v
    onstat -l

### Creating the sbspace file
The sbspace file has already been created, but this is what I did:

    su informix
    cd ~
    touch sbspace
    chmod 660 sbspace
    onspaces -c -S sbsp -p /home/informix/sbspace -o 500 -s 20480

Now run the test container     

    sudo docker run -ti -v /akka-persistence-jdbc:/akka-persistence-jdbc --link informix:informix --name test murad/java8 /bin/bash 

### Connect to the database
I used IntelliJ, added the Informix JDBC driver and used the following URL to connect:
  
    jdbc:informix-sqli://192.168.99.99:9088/test:informixserver=ol_cyklo
    
Credentials like the following:    

    hostname: 192.168.99.99 
    port: 9088    
    username: informix 
    password: informix
    database: test

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
      username ="informix"
      password = "informix"
      driverClassName = "com.informix.jdbc.IfxDriver"
      url = "jdbc:informix-sqli://"${INFORMIX_PORT_9088_TCP_ADDR}":"${INFORMIX_PORT_9088_TCP_PORT}"/test:INFORMIXSERVER=ol_cyklo"
      journalSchemaName  = ""
      journalTableName   = "journal"
      snapshotSchemaName = ""
      snapshotTableName  = "snapshot"
    }
    
In the container named 'test', type:

    cd /akka-persistence-jdbc
    ./activator "test-only akka.persistence.jdbc.journal.InformixSyncJournalSpec"
    ./activator "test-only akka.persistence.jdbc.snapshot.InformixSyncSnapshotStoreSpec"
    
# My own test setup
I use the vagrant configuration in the vagrant subdirectory:

    vagrant up
    vagrant ssh
    
Then I launch all the containers:

    sudo docker run --name postgres -d -p 5432:5432 training/postgres
    sudo docker run --name h2 -d -p 1522:1521 -P viliusl/ubuntu-h2-server
    sudo docker run --name mysql -e MYSQL_ROOT_PASSWORD=root -d -p 3306:3306 mysql
    sudo docker run --name oracle -d -p 49160:22 -p 1521:1521 -p 49162:8080 alexeiled/docker-oracle-xe-11g
    sudo docker run --name informix -ti -p 9088:9088 dnvriend/ibm-informix /bin/bash

I configure the databases, launch the database service and create the schemas when necessary, then I just launch the test suite from 
IntelliJ IDEA and/or use Activator to test. All databases in one go without installing DB services on my OSX. 
 
Gotta love Docker! :-)

Have fun!