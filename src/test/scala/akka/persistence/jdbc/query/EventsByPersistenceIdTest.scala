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

import scala.concurrent.duration._

abstract class EventsByPersistenceIdTest(config: String) extends QueryTestSpec(config) {

  it should "not find any events for unknown pid" in {
    withEventsByPersistenceId(500.millis)("unkown-pid", 0L, Long.MaxValue) { tp ⇒
      tp.request(1)
      tp.expectNoMsg(100.millis)
      tp.cancel()
      tp.expectNoMsg(100.millis)
    }
  }

  it should "find events for actor with pid 'my-1'" in {
    withTestActors { (actor1, actor2, actor3) ⇒
      withEventsByPersistenceId(500.millis)("my-1", 0, Long.MaxValue) { tp ⇒
        tp.request(10)
        tp.expectNoMsg(100.millis)

        actor1 ! 1
        tp.expectNext(EventEnvelope(1, "my-1", 1, 1))
        tp.expectNoMsg(100.millis)

        actor1 ! 2
        tp.expectNext(EventEnvelope(2, "my-1", 2, 2))
        tp.expectNoMsg(100.millis)

        tp.cancel()
        tp.expectNoMsg(100.millis)
      }
    }
  }

  it should "find events for actor with pid 'my-1' and persisting messages to other actor" in {
    withTestActors { (actor1, actor2, actor3) ⇒
      withEventsByPersistenceId(500.millis)("my-1", 0, Long.MaxValue) { tp ⇒
        tp.request(10)
        tp.expectNoMsg(100.millis)

        actor1 ! 1
        tp.expectNext(EventEnvelope(1, "my-1", 1, 1))
        tp.expectNoMsg(100.millis)

        actor1 ! 2
        tp.expectNext(EventEnvelope(2, "my-1", 2, 2))
        tp.expectNoMsg(100.millis)

        actor2 ! 1
        actor2 ! 2
        actor2 ! 3
        tp.expectNoMsg(100.millis)

        tp.cancel()
        tp.expectNoMsg(100.millis)
      }
    }
  }

  it should "find events for actor with pid 'my-2'" in {
    withTestActors { (actor1, actor2, actor3) ⇒
      actor2 ! 1
      actor2 ! 2
      actor2 ! 3

      eventually {
        journalDao.countJournal.futureValue shouldBe 3
      }

      withEventsByPersistenceId(500.millis)("my-2", 0, Long.MaxValue) { tp ⇒
        tp.request(10)
        tp.expectNext(EventEnvelope(1, "my-2", 1, 1))
        tp.expectNext(EventEnvelope(2, "my-2", 2, 2))
        tp.expectNext(EventEnvelope(3, "my-2", 3, 3))
        tp.expectNoMsg(100.millis)

        actor2 ! 5
        actor2 ! 6
        actor2 ! 7

        eventually {
          journalDao.countJournal.futureValue shouldBe 6
        }

        tp.expectNext(EventEnvelope(4, "my-2", 4, 5))
        tp.expectNext(EventEnvelope(5, "my-2", 5, 6))
        tp.expectNext(EventEnvelope(6, "my-2", 6, 7))
        tp.expectNoMsg(100.millis)

        tp.cancel()
        tp.expectNoMsg(100.millis)
      }
    }
  }
}

class PostgresEventsByPersistenceIdTest extends EventsByPersistenceIdTest("postgres-application.conf") {
  dropCreate(Postgres())
}

class MySQLEventsByPersistenceIdTest extends EventsByPersistenceIdTest("mysql-application.conf") {
  dropCreate(MySQL())
}

class OracleEventsByPersistenceIdTest extends EventsByPersistenceIdTest("oracle-application.conf") {
  dropCreate(Oracle())

  protected override def beforeEach(): Unit = {
    withStatement { stmt ⇒
      stmt.executeUpdate("""DELETE FROM "SYSTEM"."journal"""")
      stmt.executeUpdate("""DELETE FROM "SYSTEM"."deleted_to"""")
      stmt.executeUpdate("""DELETE FROM "SYSTEM"."snapshot"""")
    }
  }
}