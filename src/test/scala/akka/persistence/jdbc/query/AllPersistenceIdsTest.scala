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

import scala.concurrent.duration._

abstract class AllPersistenceIdsTest(config: String) extends QueryTestSpec(config) {
  it should "not terminate the stream when there are not pids" in
    withPersistenceIds() { tp =>
      tp.request(1)
      tp.expectNoMsg(100.millis)
      tp.cancel()
      tp.expectNoMsg(100.millis)
    }

  it should "find persistenceIds for actors" in
    withTestActors() { (actor1, actor2, actor3) =>
      withPersistenceIds() { tp =>
        tp.request(10)
        tp.expectNoMsg(100.millis)

        actor1 ! 1
        tp.expectNext(ExpectNextTimeout, "my-1")
        tp.expectNoMsg(100.millis)

        actor2 ! 1
        tp.expectNext(ExpectNextTimeout, "my-2")
        tp.expectNoMsg(100.millis)

        actor3 ! 1
        tp.expectNext(ExpectNextTimeout, "my-3")
        tp.expectNoMsg(100.millis)

        actor1 ! 1
        tp.expectNoMsg(100.millis)

        actor2 ! 1
        tp.expectNoMsg(100.millis)

        actor3 ! 1
        tp.expectNoMsg(100.millis)

        tp.cancel()
        tp.expectNoMsg(100.millis)
      }
    }
}

class PostgresScalaAllPersistenceIdsTest extends AllPersistenceIdsTest("postgres-application.conf") with ScalaJdbcReadJournalOperations with PostgresCleaner

class MySQLScalaAllPersistenceIdsTest extends AllPersistenceIdsTest("mysql-application.conf") with ScalaJdbcReadJournalOperations with MysqlCleaner

class OracleScalaAllPersistenceIdsTest extends AllPersistenceIdsTest("oracle-application.conf") with ScalaJdbcReadJournalOperations with OracleCleaner

class H2ScalaAllPersistenceIdsTest extends AllPersistenceIdsTest("h2-application.conf") with ScalaJdbcReadJournalOperations with H2Cleaner