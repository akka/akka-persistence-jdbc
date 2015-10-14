# akka-persistence-jdbc
Akka-persistence-jdbc is a plugin for akka-persistence that synchronously writes journal and snapshot entries entries to a configured JDBC store. It supports writing journal messages and snapshots to two tables: the `journal` table and the `snapshot` table.

> The build fails, this is because I am busy with Travis so it runs tests against all databases. Apologies.

Service | Status | Description
------- | ------ | -----------
License | [![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt) | Apache 2.0
Travis (master) | [![Build Status: Master](https://travis-ci.org/dnvriend/akka-persistence-jdbc.svg?branch=master)](https://travis-ci.org/dnvriend/akka-persistence-jdbc) | master branch test
Travis (2.4.0-xx) | [![Build Status: 2.4.0-xx](https://travis-ci.org/dnvriend/akka-persistence-jdbc.svg?branch=akka-2.4.0-xx)](https://travis-ci.org/dnvriend/akka-persistence-jdbc) | 2.4.0-xx branch test
Codacy | [![Codacy Badge](https://api.codacy.com/project/badge/a5d8576c2a56479ab1c40d87c78bba58)](https://www.codacy.com/app/dnvriend/akka-persistence-jdbc) | Code Quality
Bintray | [![Download](https://api.bintray.com/packages/dnvriend/maven/akka-persistence-jdbc/images/download.svg) ](https://bintray.com/dnvriend/maven/akka-persistence-jdbc/_latestVersion) | Latest Version on Bintray

By setting the appropriate Journal and SnapshotStore classes in the application.conf, you can choose the following databases:

* H2           (tested, works on 1.4.179)
* Postgresql   (tested, works on v9.4)
* MySQL        (tested, works on 5.7 MySQL Community Server (GPL))
* Oracle XE    (tested, works on Oracle XE 11g r2)

**Start of Disclaimer:**

> This plugin should not be used in production, ever! For a good, stable and scalable solution use [Apache Cassandra](http://cassandra.apache.org/) with the [akka-persistence-cassandra plugin](https://github.com/krasserm/akka-persistence-cassandra/) Only use this plug-in for study projects and proof of concepts. Please use Docker and [library/cassandra](https://registry.hub.docker.com/u/library/cassandra/) You have been warned! 

**End of Disclaimer**

# Repository
To include the JDBC plugin into your sbt project, add the following lines to your build.sbt file:

## SBT

```
resolvers += "dnvriend at bintray" at "http://dl.bintray.com/dnvriend/maven"
```

## Maven

```
<repository>
  <snapshots><enabled>false</enabled></snapshots>
  <id>central</id>
  <name>bintray</name>
  <url>http://dl.bintray.com/dnvriend/maven</url>
</repository>
```

## Latest stable release for Akka 2.3.x  

### SBT

```
libraryDependencies += "com.github.dnvriend" %% "akka-persistence-jdbc" % "1.1.9"
```

### Maven

```
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
```

## Latest stable release for Akka 2.4.x

### SBT

```
libraryDependencies += "com.github.dnvriend" %% "akka-persistence-jdbc" % "1.2.1"
```

### Maven

```
<dependency>
    <groupId>com.github.dnvriend</groupId>
    <artifactId>akka-persistence-jdbc_2.11</artifactId>
    <version>1.2.1</version>
</dependency>
```

# Usage
The user manual has been moved to [the wiki](https://github.com/dnvriend/akka-persistence-jdbc/wiki)

# What's new?
For the full list of what's new see [this wiki page] (https://github.com/dnvriend/akka-persistence-jdbc/wiki/Version-History).

## 1.2.2 (2015-10-14) - Akka v2.4.x
 - Merged PR #28 [Andrey Kouznetsov](https://github.com/prettynatty) Removing Unused ExecutionContext, thanks!
 
## 1.1.9 (2015-10-12) - Akka v2.3.x
 - scala 2.10.5 -> 2.10.6
 - akka 2.3.13 -> 2.3.14
 - scalikejdbc 2.2.7 -> 2.2.8
 
# Code of Conduct
**Contributors all agree to follow the [W3C Code of Ethics and Professional Conduct](http://www.w3.org/Consortium/cepc/).**

If you want to take action, feel free to contact Dennis Vriend <dnvriend@gmail.com>. You can also contact W3C Staff as explained in [W3C Procedures](http://www.w3.org/Consortium/pwe/#Procedures).

# License
This source code is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0). The [quick summary of what this license means is available here](https://tldrlegal.com/license/apache-license-2.0-(apache-2.0))

Have fun!
