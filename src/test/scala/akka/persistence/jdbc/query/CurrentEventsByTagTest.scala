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
import akka.persistence.jdbc.util.Schema.MySQL
import akka.persistence.jdbc.util.Schema.Postgres
import akka.persistence.jdbc.util.Schema.{ MySQL, Postgres }
import akka.persistence.query.EventEnvelope
import akka.persistence.query.EventEnvelope

abstract class CurrentEventsByTagTest(config: String) extends QueryTestSpec(config) {

}

class PostgresCurrentEventsByTagTest extends CurrentEventsByTagTest("postgres-application.conf") {
  dropCreate(Postgres())
}

class MySQLCurrentEventsByTagTest extends CurrentEventsByTagTest("mysql-application.conf") {
  dropCreate(MySQL())
}
