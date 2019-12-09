/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.query

abstract class CurrentPersistenceIdsTest(config: String) extends QueryTestSpec(config) {
  it should "not find any persistenceIds for empty journal" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    journalOps.withCurrentPersistenceIds() { tp =>
      tp.request(1)
      tp.expectComplete()
    }
  }

  it should "find persistenceIds for actors" in withActorSystem { implicit system =>
    val journalOps = new JavaDslJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      actor1 ! 1
      actor2 ! 1
      actor3 ! 1

      eventually {
        journalOps.withCurrentPersistenceIds() { tp =>
          tp.request(3)
          tp.expectNextUnordered("my-1", "my-2", "my-3")
          tp.expectComplete()
        }
      }
    }
  }
}

// Note: these tests use the shared-db configs, the test for all persistence ids use the regular db config

class PostgresScalaCurrentPersistenceIdsTest
    extends CurrentPersistenceIdsTest("postgres-shared-db-application.conf")
    with PostgresCleaner

class MySQLScalaCurrentPersistenceIdsTest
    extends CurrentPersistenceIdsTest("mysql-shared-db-application.conf")
    with MysqlCleaner

class OracleScalaCurrentPersistenceIdsTest
    extends CurrentPersistenceIdsTest("oracle-shared-db-application.conf")
    with OracleCleaner

class SqlServerScalaCurrentPersistenceIdsTest
    extends CurrentPersistenceIdsTest("sqlserver-application.conf")
    with SqlServerCleaner

class H2ScalaCurrentPersistenceIdsTest extends CurrentPersistenceIdsTest("h2-shared-db-application.conf") with H2Cleaner
