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

abstract class CurrentEventsByTagTest(config: String) extends QueryTestSpec(config) {

  it should "not find an event by tag for unknown tag" in {
    withTestActors() { (actor1, actor2, actor3) ⇒
      actor1 ! withTags(1, "one")
      actor1 ! withTags(2, "two")
      actor1 ! withTags(3, "three")

      eventually {
        journalDao.countJournal.futureValue shouldBe 3
      }

      withCurrentEventsByTag()("unknown", 0) { tp ⇒
        tp.request(Int.MaxValue)
        tp.expectComplete()
      }
    }
  }

  it should "persist and find a tagged event with one tag" in
    withTestActors() { (actor1, actor2, actor3) ⇒
      withClue("Persisting a tagged event") {
        actor1 ! withTags(1, "one")
        eventually {
          withCurrentEventsByPersistenceid()("my-1") { tp ⇒
            tp.request(Long.MaxValue)
            tp.expectNext(EventEnvelope(1, "my-1", 1, 1))
            tp.expectComplete()
          }
        }
      }

      withClue("query should find the event by tag") {
        withCurrentEventsByTag()("one", 0) { tp ⇒
          tp.request(Int.MaxValue)
          tp.expectNext(EventEnvelope(1, "my-1", 1, 1))
          tp.expectComplete()
        }
      }

      withClue("query should find the event by persistenceId") {
        withCurrentEventsByPersistenceid()("my-1", 1, 1) { tp ⇒
          tp.request(Int.MaxValue)
          tp.expectNext(EventEnvelope(1, "my-1", 1, 1))
          tp.expectComplete()
        }
      }
    }

  it should "persist and find a tagged event with multiple tags" in
    withTestActors() { (actor1, actor2, actor3) ⇒
      withClue("Persisting multiple tagged events") {
        actor1 ! withTags(1, "one", "1", "prime")
        actor1 ! withTags(2, "two", "2", "prime")
        actor1 ! withTags(3, "three", "3", "prime")
        actor1 ! withTags(4, "four", "4")
        actor1 ! withTags(5, "five", "5", "prime")

        actor2 ! withTags(3, "three", "3", "prime")
        actor3 ! withTags(3, "three", "3", "prime")

        actor1 ! 1
        actor1 ! 1

        eventually {
          journalDao.countJournal.futureValue shouldBe 9
        }
      }

      withClue("query should find events for tag 'one'") {
        withCurrentEventsByTag()("one", 0) { tp ⇒
          tp.request(Int.MaxValue)
          tp.expectNext(EventEnvelope(1, "my-1", 1, 1))
          tp.expectComplete()
        }
      }

      withClue("query should find events for tag 'prime'") {
        withCurrentEventsByTag()("prime", 0) { tp ⇒
          tp.request(Int.MaxValue)
          tp.expectNextUnordered(
            EventEnvelope(1, "my-1", 1, 1),
            EventEnvelope(2, "my-1", 2, 2),
            EventEnvelope(3, "my-1", 3, 3),
            EventEnvelope(5, "my-1", 5, 5),
            EventEnvelope(1, "my-2", 1, 3),
            EventEnvelope(1, "my-3", 1, 3)
          )
          tp.expectComplete()
        }
      }

      withClue("query should find events for tag '3'") {
        withCurrentEventsByTag()("3", 0) { tp ⇒
          tp.request(Int.MaxValue)
          tp.expectNextUnordered(
            EventEnvelope(3, "my-1", 3, 3),
            EventEnvelope(1, "my-2", 1, 3),
            EventEnvelope(1, "my-3", 1, 3)
          )
          tp.expectComplete()
        }
      }

      withClue("query should find events for tag '3'") {
        withCurrentEventsByTag()("4", 0) { tp ⇒
          tp.request(Int.MaxValue)
          tp.expectNext(EventEnvelope(4, "my-1", 4, 4))
          tp.expectComplete()
        }
      }
    }
}

class PostgresScalaCurrentEventsByTagTest extends CurrentEventsByTagTest("postgres-application.conf") with ScalaJdbcReadJournalOperations {
  dropCreate(Postgres())
}

class MySQLScalaCurrentEventsByTagTest extends CurrentEventsByTagTest("mysql-application.conf") with ScalaJdbcReadJournalOperations {
  dropCreate(MySQL())
}

class OracleScalaCurrentEventsByTagTest extends CurrentEventsByTagTest("oracle-application.conf") with ScalaJdbcReadJournalOperations {
  dropCreate(Oracle())

  protected override def beforeEach(): Unit =
    clearOracle()

  override protected def afterAll(): Unit =
    clearOracle()
}

class InMemoryScalaCurrentEventsByTagTest extends CurrentEventsByTagTest("in-memory-application.conf") with ScalaJdbcReadJournalOperations

class PostgresJavaCurrentEventsByTagTest extends CurrentEventsByTagTest("postgres-application.conf") with JavaDslJdbcReadJournalOperations {
  dropCreate(Postgres())
}

class MySQLJavaCurrentEventsByTagTest extends CurrentEventsByTagTest("mysql-application.conf") with JavaDslJdbcReadJournalOperations {
  dropCreate(MySQL())
}

class OracleJavaCurrentEventsByTagTest extends CurrentEventsByTagTest("oracle-application.conf") with JavaDslJdbcReadJournalOperations {
  dropCreate(Oracle())

  protected override def beforeEach(): Unit =
    clearOracle()

  override protected def afterAll(): Unit =
    clearOracle()
}

class InMemoryJavaCurrentEventsByTagTest extends CurrentEventsByTagTest("in-memory-application.conf") with JavaDslJdbcReadJournalOperations