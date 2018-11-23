/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

class PostgresScalaCurrentPersistenceIdsTest extends CurrentPersistenceIdsTest("postgres-shared-db-application.conf") with PostgresCleaner

class MySQLScalaCurrentPersistenceIdsTest extends CurrentPersistenceIdsTest("mysql-shared-db-application.conf") with MysqlCleaner

class OracleScalaCurrentPersistenceIdsTest extends CurrentPersistenceIdsTest("oracle-shared-db-application.conf") with OracleCleaner

class SqlServerScalaCurrentPersistenceIdsTest extends CurrentPersistenceIdsTest("sqlserver-application.conf") with SqlServerCleaner

class H2ScalaCurrentPersistenceIdsTest extends CurrentPersistenceIdsTest("h2-shared-db-application.conf") with H2Cleaner
