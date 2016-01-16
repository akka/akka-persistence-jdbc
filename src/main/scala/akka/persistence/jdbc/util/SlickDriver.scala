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

package akka.persistence.jdbc.util

import slick.driver.JdbcDriver

object SlickDriver {
  def forDriverName: PartialFunction[String, JdbcDriver] = {
    case "slick.driver.PostgresDriver" ⇒
      slick.driver.PostgresDriver
    case "slick.driver.H2Driver" ⇒
      slick.driver.H2Driver
    case "slick.driver.DerbyDriver" ⇒
      slick.driver.DerbyDriver
    case "slick.driver.HsqldbDriver" ⇒
      slick.driver.HsqldbDriver
    case "slick.driver.MySQLDriver" ⇒
      slick.driver.MySQLDriver
    case "slick.driver.SQLiteDriver" ⇒
      slick.driver.SQLiteDriver
    case "com.typesafe.slick.driver.oracle.OracleDriver" ⇒
      com.typesafe.slick.driver.oracle.OracleDriver
    case "com.typesafe.slick.driver.db2.DB2Driver" ⇒
      com.typesafe.slick.driver.db2.DB2Driver
    case "com.typesafe.slick.driver.ms.SQLServerDriver" ⇒
      com.typesafe.slick.driver.ms.SQLServerDriver
  }
}
