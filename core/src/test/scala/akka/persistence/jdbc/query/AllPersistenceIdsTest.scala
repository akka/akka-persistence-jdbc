/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.query

import scala.concurrent.duration._

abstract class AllPersistenceIdsTest(config: String) extends QueryTestSpec(config) {
  it should "not terminate the stream when there are not pids" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    journalOps.withPersistenceIds() { tp =>
      tp.request(1)
      tp.expectNoMessage(100.millis)
      tp.cancel()
      tp.expectNoMessage(100.millis)
    }
  }

  it should "find persistenceIds for actors" in withActorSystem { implicit system =>
    val journalOps = new JavaDslJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      journalOps.withPersistenceIds() { tp =>
        tp.request(10)
        tp.expectNoMessage(100.millis)

        actor1 ! 1
        tp.expectNext(ExpectNextTimeout, "my-1")
        tp.expectNoMessage(100.millis)

        actor2 ! 1
        tp.expectNext(ExpectNextTimeout, "my-2")
        tp.expectNoMessage(100.millis)

        actor3 ! 1
        tp.expectNext(ExpectNextTimeout, "my-3")
        tp.expectNoMessage(100.millis)

        actor1 ! 1
        tp.expectNoMessage(100.millis)

        actor2 ! 1
        tp.expectNoMessage(100.millis)

        actor3 ! 1
        tp.expectNoMessage(100.millis)

        tp.cancel()
        tp.expectNoMessage(100.millis)
      }
    }
  }
}

class PostgresScalaAllPersistenceIdsTest extends AllPersistenceIdsTest("postgres-application.conf") with PostgresCleaner

class MySQLScalaAllPersistenceIdsTest extends AllPersistenceIdsTest("mysql-application.conf") with MysqlCleaner

class OracleScalaAllPersistenceIdsTest extends AllPersistenceIdsTest("oracle-application.conf") with OracleCleaner

class SqlServerScalaAllPersistenceIdsTest
    extends AllPersistenceIdsTest("sqlserver-application.conf")
    with SqlServerCleaner

class H2ScalaAllPersistenceIdsTest extends AllPersistenceIdsTest("h2-application.conf") with H2Cleaner
