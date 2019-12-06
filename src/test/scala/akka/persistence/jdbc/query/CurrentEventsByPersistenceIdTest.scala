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

import akka.Done
import akka.persistence.Persistence
import akka.persistence.jdbc.journal.JdbcAsyncWriteJournal
import akka.persistence.query.{ EventEnvelope, Sequence }
import akka.testkit.TestProbe

abstract class CurrentEventsByPersistenceIdTest(config: String) extends QueryTestSpec(config) {
  it should "not find any events for unknown pid" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    journalOps.withCurrentEventsByPersistenceId()("unkown-pid", 0L, Long.MaxValue) { tp =>
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }
  }

  it should "find events from an offset" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      actor1 ! 1
      actor1 ! 2
      actor1 ! 3
      actor1 ! 4

      eventually {
        journalOps.countJournal.futureValue shouldBe 4
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 0, 1) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 1, 1) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 1, 2) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 2, 2) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 2, 3) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 3, 3) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 0, 3) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 1, 3) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNext(EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.expectNext(EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.expectComplete()
      }
    }
  }

  it should "find events for actors" in withActorSystem { implicit system =>
    val journalOps = new JavaDslJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      actor1 ! 1
      actor1 ! 2
      actor1 ! 3

      eventually {
        journalOps.countJournal.futureValue shouldBe 3
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 1, 1) { tp =>
        tp.request(Int.MaxValue).expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1)).expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 2, 2) { tp =>
        tp.request(Int.MaxValue).expectNext(EventEnvelope(Sequence(2), "my-1", 2, 2)).expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 3, 3) { tp =>
        tp.request(Int.MaxValue).expectNext(EventEnvelope(Sequence(3), "my-1", 3, 3)).expectComplete()
      }

      journalOps.withCurrentEventsByPersistenceId()("my-1", 2, 3) { tp =>
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(Sequence(2), "my-1", 2, 2))
          .expectNext(EventEnvelope(Sequence(3), "my-1", 3, 3))
          .expectComplete()
      }
    }
  }

  it should "allow updating events (for data migrations)" in withActorSystem { implicit system =>
    val journalOps = new JavaDslJdbcReadJournalOperations(system)
    val journal = Persistence(system).journalFor("")

    withTestActors() { (actor1, _, _) =>
      actor1 ! 1
      actor1 ! 2
      actor1 ! 3

      eventually {
        journalOps.countJournal.futureValue shouldBe 3
      }

      val pid = "my-1"
      journalOps.withCurrentEventsByPersistenceId()(pid, 1, 3) { tp =>
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(Sequence(1), pid, 1, 1))
          .expectNext(EventEnvelope(Sequence(2), pid, 2, 2))
          .expectNext(EventEnvelope(Sequence(3), pid, 3, 3))
          .expectComplete()
      }

      // perform in-place update
      val journalP = TestProbe()
      journal.tell(JdbcAsyncWriteJournal.InPlaceUpdateEvent(pid, 1, Integer.valueOf(111)), journalP.ref)
      journalP.expectMsg(Done)

      journalOps.withCurrentEventsByPersistenceId()(pid, 1, 3) { tp =>
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(Sequence(1), pid, 1, Integer.valueOf(111)))
          .expectNext(EventEnvelope(Sequence(2), pid, 2, 2))
          .expectNext(EventEnvelope(Sequence(3), pid, 3, 3))
          .expectComplete()
      }
    }
  }
}

// Note: these tests use the shared-db configs, the test for all (so not only current) events use the regular db config

class PostgresScalaCurrentEventsByPersistenceIdTest
    extends CurrentEventsByPersistenceIdTest("postgres-shared-db-application.conf")
    with PostgresCleaner

class MySQLScalaCurrentEventsByPersistenceIdTest
    extends CurrentEventsByPersistenceIdTest("mysql-shared-db-application.conf")
    with MysqlCleaner

class OracleScalaCurrentEventsByPersistenceIdTest
    extends CurrentEventsByPersistenceIdTest("oracle-shared-db-application.conf")
    with OracleCleaner

class SqlServerScalaCurrentEventsByPersistenceIdTest
    extends CurrentEventsByPersistenceIdTest("sqlserver-shared-db-application.conf")
    with SqlServerCleaner

class H2ScalaCurrentEventsByPersistenceIdTest
    extends CurrentEventsByPersistenceIdTest("h2-shared-db-application.conf")
    with H2Cleaner
