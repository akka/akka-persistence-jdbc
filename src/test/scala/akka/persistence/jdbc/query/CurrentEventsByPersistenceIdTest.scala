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

import akka.persistence.jdbc.util.Schema.{ MySQL, Oracle, Postgres }
import akka.persistence.query.EventEnvelope

abstract class CurrentEventsByPersistenceIdTest(config: String) extends QueryTestSpec(config) {

  it should "not find any events for unknown pid" in
    withCurrentEventsByPersistenceid()("unkown-pid", 0L, Long.MaxValue) { tp ⇒
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }

  it should "find events from an offset" in {
    withTestActors() { (actor1, actor2, actor3) ⇒
      actor1 ! 1
      actor1 ! 2
      actor1 ! 3
      actor1 ! 4

      eventually {
        journalDao.countJournal.futureValue shouldBe 4
      }

      withCurrentEventsByPersistenceid()("my-1", 2, 3) { tp ⇒
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(2, "my-1", 2, 2))
        tp.expectNext(EventEnvelope(3, "my-1", 3, 3))
        tp.expectComplete()
      }
    }
  }

  it should "find events for actors" in
    withTestActors() { (actor1, actor2, actor3) ⇒
      actor1 ! 1
      actor1 ! 2
      actor1 ! 3

      eventually {
        journalDao.countJournal.futureValue shouldBe 3
      }

      And("The events can be queried by offset 1, 1")
      withCurrentEventsByPersistenceid()("my-1", 1, 1) { tp ⇒
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(1, "my-1", 1, 1))
          .expectComplete()
      }

      And("The events can be queried by offset 2, 2")
      withCurrentEventsByPersistenceid()("my-1", 2, 2) { tp ⇒
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(2, "my-1", 2, 2))
          .expectComplete()
      }

      And("The events can be queried by offset 3, 3")
      withCurrentEventsByPersistenceid()("my-1", 3, 3) { tp ⇒
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(3, "my-1", 3, 3))
          .expectComplete()
      }

      And("The events can be queried by offset 2, 3")
      withCurrentEventsByPersistenceid()("my-1", 2, 3) { tp ⇒
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(2, "my-1", 2, 2), EventEnvelope(3, "my-1", 3, 3))
          .expectComplete()
      }
    }
}

class PostgresScalaCurrentEventsByPersistenceIdTest extends CurrentEventsByPersistenceIdTest("postgres-application.conf") with ScalaJdbcReadJournalOperations {
  dropCreate(Postgres())
}

class MySQLScalaCurrentEventsByPersistenceIdTest extends CurrentEventsByPersistenceIdTest("mysql-application.conf") with ScalaJdbcReadJournalOperations {
  dropCreate(MySQL())
}

class OracleScalaCurrentEventsByPersistenceIdTest extends CurrentEventsByPersistenceIdTest("oracle-application.conf") with ScalaJdbcReadJournalOperations {
  dropCreate(Oracle())

  protected override def beforeEach(): Unit =
    clearOracle()

  override protected def afterAll(): Unit =
    clearOracle()
}

class PostgresJavaCurrentEventsByPersistenceIdTest extends CurrentEventsByPersistenceIdTest("postgres-application.conf") with JavaDslJdbcReadJournalOperations {
  dropCreate(Postgres())
}

class MySQLJavaCurrentEventsByPersistenceIdTest extends CurrentEventsByPersistenceIdTest("mysql-application.conf") with JavaDslJdbcReadJournalOperations {
  dropCreate(MySQL())
}

class OracleJavaCurrentEventsByPersistenceIdTest extends CurrentEventsByPersistenceIdTest("oracle-application.conf") with JavaDslJdbcReadJournalOperations {
  dropCreate(Oracle())

  protected override def beforeEach(): Unit =
    clearOracle()

  override protected def afterAll(): Unit =
    clearOracle()
}