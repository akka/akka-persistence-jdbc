# akka-persistence-jdbc
The akka-persistence-jdbc is a plugin for [akka-persistence](http://doc.akka.io/docs/akka/snapshot/scala/persistence.html) that writes journal entries to a configured JDBC database. It supports writing journal messages and snapshots to two tables:  the `journal` table and the `snapshot` table. 

[![Build Status](https://travis-ci.org/dnvriend/akka-persistence-jdbc.svg?branch=master)](https://travis-ci.org/dnvriend/akka-persistence-jdbc)
[![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

By setting the appropriate Journal and SnapshotStore classes in the application.conf, you can choose the following databases:

* H2           (tested, works on 1.4.179)
* Postgresql   (tested, works on v9.3.1)
* MySQL        (tested, works on 5.6.19 MySQL Community Server (GPL))
* Oracle XE    (tested, works on Oracle XE 11g r2)

**Start of Disclaimer:**

> This plugin should not be used in production, ever! For a good, stable and scalable solution use [Apache Cassandra](http://cassandra.apache.org/) with the [akka-persistence-cassandra plugin](https://github.com/krasserm/akka-persistence-cassandra/) Only use this plug-in for study projects and proof of concepts. Please use Docker and [library/cassandra](https://registry.hub.docker.com/u/library/cassandra/) You have been warned! 

**End of Disclaimer**

# Dependency
To include the JDBC plugin into your sbt project, add the following lines to your build.sbt file:

    resolvers += "dnvriend at bintray" at "http://dl.bintray.com/dnvriend/maven"

    libraryDependencies += "com.github.dnvriend" %% "akka-persistence-jdbc" % "1.2.0-RC2"

For Maven users, add the following to the pom.xml
    
    <dependency>
        <groupId>com.github.dnvriend</groupId>
        <artifactId>akka-persistence-jdbc_2.11</artifactId>
        <version>1.2.0-RC2</version>
    </dependency>

Add the following to the repositories section of the pom:

    <repository>
      <snapshots><enabled>false</enabled></snapshots>
      <id>central</id>
      <name>bintray</name>
      <url>http://dl.bintray.com/dnvriend/maven</url>
    </repository>

# What's new?

## 1.2.0-RC2 (2015-09-07)
 - Compatibility with Akka 2.4.0-RC2 
 - Use the following library dependency: "com.github.dnvriend" %% "akka-persistence-jdbc" % "1.2.0-RC2"
 - Fully backwards compatible with akka-persistence-jdbc v1.1.7's schema and configuration
 - Please note; a schema, serialization and code refactoring will be iteratively applied on newer release, but for each step, a migration guide will be available.