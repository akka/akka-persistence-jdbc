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

class EventsByPersistenceIdAndTagTest(config: String) extends QueryTestSpec(config) {

  it should "not find events for unknown tags" in
    withTestActors { (actor1, actor2, actor3) ⇒
      actor1 ! withTags(1, "one")
      actor1 ! withTags(2, "two")
      actor1 ! withTags(3, "three")

      eventually {
        journalDao.countJournal.futureValue shouldBe 3
      }

      withEventsByPersistenceIdAndTag()("unknown-pid", "unknown", 0) { tp ⇒
        tp.request(Int.MaxValue)
        tp.expectNoMsg(100.millis)
        tp.cancel()
        tp.expectNoMsg(100.millis)
      }
    }

  it should "persist and find tagged event for by tag 'one' and pid 'my-1'" in
    withTestActors { (actor1, actor2, actor3) ⇒
      withEventsByPersistenceIdAndTag(10.seconds)("my-1", "one", 0) { tp ⇒
        tp.request(Int.MaxValue)
        tp.expectNoMsg(100.millis)

        actor1 ! withTags(1, "one")
        tp.expectNext(EventEnvelope(1, "my-1", 1, 1))
        tp.expectNoMsg(100.millis)

        actor2 ! withTags(1, "one")
        tp.expectNoMsg(100.millis)

        actor3 ! withTags(1, "one")
        tp.expectNoMsg(100.millis)

        actor1 ! withTags(2, "two")
        actor2 ! withTags(2, "two")
        actor3 ! withTags(2, "two")
        tp.expectNoMsg(100.millis)

        actor1 ! withTags(3, "one")
        tp.expectNext(EventEnvelope(3, "my-1", 3, 3))
        tp.expectNoMsg(100.millis)

        actor2 ! withTags(3, "one")
        tp.expectNoMsg(100.millis)

        actor3 ! withTags(3, "one")
        tp.expectNoMsg(100.millis)

        actor1 ! withTags(1, "one")
        tp.expectNext(EventEnvelope(4, "my-1", 4, 1))
        tp.expectNoMsg(100.millis)

        tp.cancel()
        tp.expectNoMsg(100.millis)
      }
    }
}

class PostgresEventsByPersistenceIdAndTagTest extends EventsByPersistenceIdAndTagTest("postgres-application.conf") {
  dropCreate(Postgres())
}

class MySQLEventsByPersistenceIdAndTagTest extends EventsByPersistenceIdAndTagTest("mysql-application.conf") {
  dropCreate(MySQL())
}

class OracleEventsByPersistenceIdAndTagTest extends EventsByPersistenceIdAndTagTest("oracle-application.conf") {
  dropCreate(Oracle())

  protected override def beforeEach(): Unit =
    clearOracle()

  override protected def afterAll(): Unit =
    clearOracle()
}
