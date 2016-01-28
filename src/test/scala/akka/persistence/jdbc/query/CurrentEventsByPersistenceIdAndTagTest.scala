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

import akka.persistence.jdbc.util.Schema.{ Oracle, MySQL, Postgres }
import akka.persistence.query.EventEnvelope

abstract class CurrentEventsByPersistenceIdAndTagTest(config: String) extends QueryTestSpec(config) {

  it should "not find an event by tag for unknown tag" in {
    withTestActors { (actor1, actor2, actor3) ⇒
      actor1 ! withTags(1, "one")
      actor1 ! withTags(2, "two")
      actor1 ! withTags(3, "three")

      eventually {
        journalDao.countJournal.futureValue shouldBe 3
      }

      withCurrentEventsByPersistenceIdAndTag()("pid", "unknown", 0) { tp ⇒
        tp.request(Int.MaxValue)
        tp.expectComplete()
      }
    }
  }

  it should "persist and find a tagged event with one tag for persistence id" in
    withTestActors { (actor1, actor2, actor3) ⇒
      withClue("Persisting a tagged event") {
        actor1 ! withTags(1, "one")
        actor1 ! withTags(2, "two")
        eventually {
          withCurrentEventsByPersistenceid()("my-1") { tp ⇒
            tp.request(Long.MaxValue)
            tp.expectNext(EventEnvelope(1, "my-1", 1, 1))
            tp.expectNext(EventEnvelope(2, "my-1", 2, 2))
            tp.expectComplete()
          }
        }
      }

      withClue("query should find the event by persistenceId and tag") {
        withCurrentEventsByPersistenceIdAndTag()("my-1", "one", 0) { tp ⇒
          tp.request(Int.MaxValue)
          tp.expectNext(EventEnvelope(1, "my-1", 1, 1))
          tp.expectComplete()
        }
      }

      withClue("query should find the event by persistenceId and tag") {
        withCurrentEventsByPersistenceIdAndTag()("my-1", "two", 0) { tp ⇒
          tp.request(Int.MaxValue)
          tp.expectNext(EventEnvelope(2, "my-1", 2, 2))
          tp.expectComplete()
        }
      }
    }
}

class PostgresScalaCurrentEventsByPersistenceIdAndTagTest extends CurrentEventsByPersistenceIdAndTagTest("postgres-application.conf") with ScalaJdbcReadJournalOperations {
  dropCreate(Postgres())
}

class MySQLScalaCurrentEventsByPersistenceIdAndTagTest extends CurrentEventsByPersistenceIdAndTagTest("mysql-application.conf") with ScalaJdbcReadJournalOperations {
  dropCreate(MySQL())
}

class OracleScalaCurrentEventsByPersistenceIdAndTagTest extends CurrentEventsByPersistenceIdAndTagTest("oracle-application.conf") with ScalaJdbcReadJournalOperations {
  dropCreate(Oracle())

  protected override def beforeEach(): Unit =
    clearOracle()

  override protected def afterAll(): Unit =
    clearOracle()
}

class PostgresJavaCurrentEventsByPersistenceIdAndTagTest extends CurrentEventsByPersistenceIdAndTagTest("postgres-application.conf") with JavaDslJdbcReadJournalOperations {
  dropCreate(Postgres())
}

class MySQLJavaCurrentEventsByPersistenceIdAndTagTest extends CurrentEventsByPersistenceIdAndTagTest("mysql-application.conf") with JavaDslJdbcReadJournalOperations {
  dropCreate(MySQL())
}

class OracleJavaCurrentEventsByPersistenceIdAndTagTest extends CurrentEventsByPersistenceIdAndTagTest("oracle-application.conf") with JavaDslJdbcReadJournalOperations {
  dropCreate(Oracle())

  protected override def beforeEach(): Unit =
    clearOracle()

  override protected def afterAll(): Unit =
    clearOracle()
}
