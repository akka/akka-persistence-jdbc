# akka-persistence-jdbc

## A JDBC plugin for [akka-persistence](http://akka.io)

 * Note: this is a work in progress, please don't use in production!!

### Outstanding tasks:

 - Issues with the Journal
 - No snapshot functionality
 - Developing and testing against PostgreSQL/9.3.1 

### What's new?

#### 0.0.1
 - Cross compile to 2.11 (Issue #9)
 - Update to 2.11 compatible versions of libraries (scalatest, casbah); Mark akka dependencies `provided`
 - Update to support 0.3.1 of [TCK](https://github.com/krasserm/akka-persistence-testkit), which supports Scala 2.11
 - Eliminate publish message from root project
 - Remove build and publish of rxmongo project for now
