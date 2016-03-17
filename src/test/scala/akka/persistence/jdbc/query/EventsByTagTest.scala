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
import scala.concurrent.duration._

abstract class EventsByTagTest(config: String) extends QueryTestSpec(config) {
  it should "not find events for unknown tags" in {
    withTestActors() { (actor1, actor2, actor3) ⇒
      actor1 ! withTags(1, "one")
      actor2 ! withTags(2, "two")
      actor3 ! withTags(3, "three")

      eventually {
        journalDao.countJournal.futureValue shouldBe 3
      }

      withEventsByTag()("unknown", 0) { tp ⇒
        tp.request(Int.MaxValue)
        tp.expectNoMsg(100.millis)
        tp.cancel()
        tp.expectNoMsg(100.millis)
      }
    }
  }

  it should "find all events by tag" in {
    withTestActors() { (actor1, actor2, actor3) ⇒
      actor1 ! withTags(1, "number")
      actor2 ! withTags(2, "number")
      actor3 ! withTags(3, "number")

      eventually {
        journalDao.countJournal.futureValue shouldBe 3
      }

      withEventsByTag()("number", 0) { tp ⇒
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(1, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(2, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(3, _, _, _) ⇒ }
        tp.expectNoMsg(100.millis)

        actor1 ! withTags(4, "number")
        tp.expectNextPF { case EventEnvelope(4, _, _, _) ⇒ }
        tp.cancel()
        tp.expectNoMsg(100.millis)
      }
    }
  }

  it should "find events by tag from an offset" in {
    withTestActors() { (actor1, actor2, actor3) ⇒
      actor1 ! withTags(1, "number")
      actor2 ! withTags(2, "number")
      actor3 ! withTags(3, "number")

      eventually {
        journalDao.countJournal.futureValue shouldBe 3
      }

      withEventsByTag()("number", 2) { tp ⇒
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(3, _, _, _) ⇒ }
        tp.expectNoMsg(100.millis)

        actor1 ! withTags(4, "number")
        tp.expectNextPF { case EventEnvelope(4, _, _, _) ⇒ }
        tp.cancel()
        tp.expectNoMsg(100.millis)
      }
    }
  }

  it should "persist and find tagged event for one tag" in {
    withTestActors() { (actor1, actor2, actor3) ⇒
      withEventsByTag(10.seconds)("one", 0) { tp ⇒
        tp.request(Int.MaxValue)
        tp.expectNoMsg(100.millis)

        actor1 ! withTags(1, "one")
        tp.expectNextPF { case EventEnvelope(1, _, _, _) ⇒ }
        tp.expectNoMsg(100.millis)

        actor2 ! withTags(1, "one")
        tp.expectNextPF { case EventEnvelope(2, _, _, _) ⇒ }
        tp.expectNoMsg(100.millis)

        actor3 ! withTags(1, "one")
        tp.expectNextPF { case EventEnvelope(3, _, _, _) ⇒ }
        tp.expectNoMsg(100.millis)

        actor1 ! withTags(2, "two")
        tp.expectNoMsg(100.millis)
        actor2 ! withTags(2, "two")
        tp.expectNoMsg(100.millis)
        actor3 ! withTags(2, "two")
        tp.expectNoMsg(100.millis)

        actor1 ! withTags(3, "one")
        tp.expectNextPF { case EventEnvelope(4, _, _, _) ⇒ }
        tp.expectNoMsg(100.millis)

        actor2 ! withTags(3, "one")
        tp.expectNextPF { case EventEnvelope(5, _, _, _) ⇒ }
        tp.expectNoMsg(100.millis)

        actor3 ! withTags(3, "one")
        tp.expectNextPF { case EventEnvelope(6, _, _, _) ⇒ }
        tp.expectNoMsg(100.millis)

        tp.cancel()
        tp.expectNoMsg(100.millis)
      }
    }
  }

  it should "persist and find tagged events when stored with multiple tags" in {
    withTestActors() { (actor1, actor2, actor3) ⇒
      actor1 ! withTags(1, "one", "1", "prime")
      actor1 ! withTags(2, "two", "2", "prime")
      actor1 ! withTags(3, "three", "3", "prime")
      actor1 ! withTags(4, "four", "4")
      actor1 ! withTags(5, "five", "5", "prime")
      actor2 ! withTags(3, "three", "3", "prime")
      actor3 ! withTags(3, "three", "3", "prime")

      actor1 ! 6
      actor1 ! 7
      actor1 ! 8
      actor1 ! 9
      actor1 ! 10

      eventually {
        journalDao.countJournal.futureValue shouldBe 12
      }

      withEventsByTag(10.seconds)("prime", 0) { tp ⇒
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(1, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(2, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(3, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(4, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(5, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(6, _, _, _) ⇒ }
        tp.expectNoMsg(100.millis)
        tp.cancel()
      }

      withEventsByTag(10.seconds)("three", 0) { tp ⇒
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(1, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(2, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(3, _, _, _) ⇒ }
        tp.expectNoMsg(100.millis)
        tp.cancel()
      }

      withEventsByTag(10.seconds)("3", 0) { tp ⇒
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(1, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(2, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(3, _, _, _) ⇒ }
        tp.expectNoMsg(100.millis)
        tp.cancel()
      }

      withEventsByTag(10.seconds)("one", 0) { tp ⇒
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(1, _, _, _) ⇒ }
        tp.expectNoMsg(100.millis)
        tp.cancel()
      }

      withEventsByPersistenceId()("my-1", 0, Int.MaxValue) { tp ⇒
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(1, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(2, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(3, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(4, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(5, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(6, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(7, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(8, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(9, _, _, _) ⇒ }
        tp.expectNextPF { case EventEnvelope(10, _, _, _) ⇒ }
        tp.expectNoMsg(100.millis)
        tp.cancel()
      }
    }
  }
}

class PostgresScalaEventsByTagTest extends EventsByTagTest("postgres-application.conf") with ScalaJdbcReadJournalOperations {
  dropCreate(Postgres())
}

class MySQLScalaEventByTagTest extends EventsByTagTest("mysql-application.conf") with ScalaJdbcReadJournalOperations {
  dropCreate(MySQL())
}

class OracleScalaEventByTagTest extends EventsByTagTest("oracle-application.conf") with ScalaJdbcReadJournalOperations {
  dropCreate(Oracle())

  protected override def beforeEach(): Unit =
    clearOracle()

  override protected def afterAll(): Unit =
    clearOracle()
}

class InMemoryScalaEventsByTagTest extends EventsByTagTest("in-memory-application.conf") with ScalaJdbcReadJournalOperations

class PostgresJavaEventsByTagTest extends EventsByTagTest("postgres-application.conf") with JavaDslJdbcReadJournalOperations {
  dropCreate(Postgres())
}

class MySQLJavaEventByTagTest extends EventsByTagTest("mysql-application.conf") with JavaDslJdbcReadJournalOperations {
  dropCreate(MySQL())
}

class OracleJavaEventByTagTest extends EventsByTagTest("oracle-application.conf") with JavaDslJdbcReadJournalOperations {
  dropCreate(Oracle())

  protected override def beforeEach(): Unit =
    clearOracle()

  override protected def afterAll(): Unit =
    clearOracle()
}

class InMemoryJavaEventsByTagTest extends EventsByTagTest("in-memory-application.conf") with JavaDslJdbcReadJournalOperations
