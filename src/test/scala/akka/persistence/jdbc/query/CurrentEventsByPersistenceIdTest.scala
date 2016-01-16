/*
 * Copyright 2015 Dennis Vriend
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

import akka.persistence.jdbc.util.Schema
import akka.persistence.query.EventEnvelope

abstract class CurrentEventsByPersistenceIdTest(config: String) extends QueryTestSpec(config) {

  it should "not find any events for unknown pid" in {
    When("All events of unknown pid are requested")
    Then("No events should be found")
    withCurrentEventsByPersistenceid("unkown-pid", 0L, Long.MaxValue) { tp ⇒
      tp.request(Int.MaxValue)
      tp.expectComplete()
    }
  }

  it should "find events for actors" in {
    Given("Three persistent actors")
    withTestActors { (actor1, actor2, actor3) ⇒
      When("Events are stored")
      actor1 ! 1
      actor1 ! 2
      actor1 ! 3

      Then("Eventually three events are available in the journal")
      eventually {
        withCurrentEventsByPersistenceid("my-1", 0, Long.MaxValue) { tp ⇒
          tp.request(Int.MaxValue)
            .expectNextN((1 to 3).toList.map(i ⇒ EventEnvelope(i, "my-1", i, i)))
            .expectComplete()
        }
      }

      And("The events can be queried by offset 1, 1")
      withCurrentEventsByPersistenceid("my-1", 1, 1) { tp ⇒
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(1, "my-1", 1, 1))
          .expectComplete()
      }

      And("The events can be queried by offset 2, 2")
      withCurrentEventsByPersistenceid("my-1", 2, 2) { tp ⇒
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(2, "my-1", 2, 2))
          .expectComplete()
      }

      And("The events can be queried by offset 3, 3")
      withCurrentEventsByPersistenceid("my-1", 3, 3) { tp ⇒
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(3, "my-1", 3, 3))
          .expectComplete()
      }

      And("The events can be queried by offset 2, 3")
      withCurrentEventsByPersistenceid("my-1", 2, 3) { tp ⇒
        tp.request(Int.MaxValue)
          .expectNext(EventEnvelope(2, "my-1", 2, 2), EventEnvelope(3, "my-1", 3, 3))
          .expectComplete()
      }
    }
  }
}

class PostgresCurrentEventsByPersistenceIdTest extends CurrentEventsByPersistenceIdTest("postgres-application.conf") {
  dropCreate(Schema.Postgres)
}

class MySQLCurrentEventsByPersistenceIdTest extends CurrentEventsByPersistenceIdTest("mysql-application.conf") {
  dropCreate(Schema.MySQL)
}
