# akka-persistence-jdbc
A JDBC plugin for [akka-persistence](http://akka.io), a great plugin for Akka created by [Martin Krasser's](https://github.com/krasserm)

 - Note: this is a work in progress, please don't use in production!!

### Todo:

 - Snapshotting; no support for it now, only the Journal
 - Only tested it against PostgreSQL/9.3.1

### What's new?

### 0.0.3
 - Using [Martin Krasser's](https://github.com/krasserm) [akka-persistence-testkit](https://github.com/krasserm/akka-persistence-testkit)
  to test the akka-persistence-jdbc plugin. 
 - Upgrade to Akka 2.3.4

#### 0.0.2
 - Using [ScalikeJdbc](http://scalikejdbc.org/) as the JDBC access library instead of my home-brew one. 

#### 0.0.1
 - Initial commit