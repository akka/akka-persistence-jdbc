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

import akka.persistence.query.{EventEnvelope, Sequence}

import scala.concurrent.duration._

abstract class EventsByPersistenceIdTest(config: String) extends QueryTestSpec(config) {

  it should "not find any events for unknown pid" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    journalOps.withEventsByPersistenceId()("unkown-pid", 0L, Long.MaxValue) { tp =>
      tp.request(1)
      tp.expectNoMessage(100.millis)
      tp.cancel()
      tp.expectNoMessage(100.millis)
    }
  }

  it should "find events from an offset" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      actor1 ! withTags(1, "number")
      actor1 ! withTags(2, "number")
      actor1 ! withTags(3, "number")
      actor1 ! withTags(4, "number")

      eventually {
        journalOps.countJournal.futureValue shouldBe 4
      }

      journalOps.withEventsByPersistenceId()("my-1", 0, 0) { tp =>
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-1", 0, 1) { tp =>
        tp.request(1)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-1", 1, 1) { tp =>
        tp.request(1)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-1", 1, 2) { tp =>
        tp.request(1)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.request(1)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-1", 2, 2) { tp =>
        tp.request(1)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-1", 2, 3) { tp =>
        tp.request(1)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.request(1)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-1", 3, 3) { tp =>
        tp.request(1)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-1", 0, 3) { tp =>
        tp.request(1)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.request(1)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.request(1)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }

      journalOps.withEventsByPersistenceId()("my-1", 1, 3) { tp =>
        tp.request(1)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.request(1)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.request(1)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.request(1)
        tp.expectComplete()
        tp.cancel()
      }
    }
  }

  it should "find events for actor with pid 'my-1'" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      journalOps.withEventsByPersistenceId()("my-1", 0) { tp =>
        tp.request(10)
        tp.expectNoMessage(100.millis)

        actor1 ! 1
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNoMessage(100.millis)

        actor1 ! 2
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.expectNoMessage(100.millis)
        tp.cancel()
      }
    }
  }

  it should "find events for actor with pid 'my-1' and persisting messages to other actor" in withActorSystem { implicit system =>
    val journalOps = new JavaDslJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      journalOps.withEventsByPersistenceId()("my-1", 0, Long.MaxValue) { tp =>
        tp.request(10)
        tp.expectNoMessage(100.millis)

        actor1 ! 1
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-1", 1, 1))
        tp.expectNoMessage(100.millis)

        actor1 ! 2
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-1", 2, 2))
        tp.expectNoMessage(100.millis)

        actor2 ! 1
        actor2 ! 2
        actor2 ! 3
        tp.expectNoMessage(100.millis)

        actor1 ! 3
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(3), "my-1", 3, 3))
        tp.expectNoMessage(100.millis)

        tp.cancel()
        tp.expectNoMessage(100.millis)
      }
    }
  }

  it should "find events for actor with pid 'my-2'" in withActorSystem { implicit system =>
    val journalOps = new JavaDslJdbcReadJournalOperations(system)
    withTestActors() { (actor1, actor2, actor3) =>
      actor2 ! 1
      actor2 ! 2
      actor2 ! 3

      eventually {
        journalOps.countJournal.futureValue shouldBe 3
      }

      journalOps.withEventsByPersistenceId()("my-2", 0, Long.MaxValue) { tp =>
        tp.request(1)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(1), "my-2", 1, 1))
        tp.request(1)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(2), "my-2", 2, 2))
        tp.request(1)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(3), "my-2", 3, 3))
        tp.expectNoMessage(100.millis)

        actor2 ! 5
        actor2 ! 6
        actor2 ! 7

        eventually {
          journalOps.countJournal.futureValue shouldBe 6
        }

        tp.request(3)
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(4), "my-2", 4, 5))
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(5), "my-2", 5, 6))
        tp.expectNext(ExpectNextTimeout, EventEnvelope(Sequence(6), "my-2", 6, 7))
        tp.expectNoMessage(100.millis)

        tp.cancel()
        tp.expectNoMessage(100.millis)
      }
    }
  }
}

class PostgresScalaEventsByPersistenceIdTest extends EventsByPersistenceIdTest("postgres-application.conf") with PostgresCleaner

class MySQLScalaEventsByPersistenceIdTest extends EventsByPersistenceIdTest("mysql-application.conf") with MysqlCleaner

// class OracleScalaEventsByPersistenceIdTest extends EventsByPersistenceIdTest("oracle-application.conf") with OracleCleaner

class H2ScalaEventsByPersistenceIdTest extends EventsByPersistenceIdTest("h2-application.conf") with H2Cleaner