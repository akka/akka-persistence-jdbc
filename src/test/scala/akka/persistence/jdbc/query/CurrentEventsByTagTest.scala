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

import akka.persistence.jdbc.util.Schema
import akka.persistence.jdbc.util.Schema.MySQL
import akka.persistence.jdbc.util.Schema.Postgres
import akka.persistence.jdbc.util.Schema.{ MySQL, Postgres }
import akka.persistence.journal.Tagged
import akka.persistence.query.EventEnvelope
import akka.persistence.query.EventEnvelope
import org.scalatest.Ignore

abstract class CurrentEventsByTagTest(config: String) extends QueryTestSpec(config) {

  it should "not find any events for unknown tag" in
    withTestActors { (actor1, actor2, actor3) ⇒
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

      //      withCurrentEventsByPersistenceid()("unkown-pid", 0L, Long.MaxValue) { tp ⇒
      //        tp.request(Int.MaxValue)
      //        tp.expectComplete()
      //      }
    }

}

@Ignore
class PostgresCurrentEventsByTagTest extends CurrentEventsByTagTest("postgres-application.conf") {
  dropCreate(Postgres())
}

//class MySQLCurrentEventsByTagTest extends CurrentEventsByTagTest("mysql-application.conf") {
//  dropCreate(MySQL())
//}
