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

package akka.persistence.jdbc.journal

import akka.persistence.jdbc.common.PluginConfig
import akka.persistence.jdbc.extension.ScalikeExtension
import akka.persistence.jdbc.util._
import akka.persistence.journal.JournalSpec
import com.typesafe.config.{ Config, ConfigFactory }
import scalikejdbc.DBSession

abstract class JdbcSyncJournalSpec(config: Config) extends JournalSpec(config) with JdbcInit {
  val cfg = PluginConfig(system)

  override def beforeAll() {
    dropJournalTable()
    createJournalTable()
    dropSnapshotTable()
    createSnapshotTable()
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
  }
}

abstract class GenericJdbcJournalSpec(config: Config) extends JdbcSyncJournalSpec(config) {
  implicit val session: DBSession = ScalikeExtension(system).session
}

class H2SyncJournalSpec extends GenericJdbcJournalSpec(config = ConfigFactory.load("h2-application.conf")) with H2JdbcInit

class PostgresqlSyncJournalSpec extends GenericJdbcJournalSpec(config = ConfigFactory.load("postgres-application.conf")) with PostgresqlJdbcInit

class MysqlSyncJournalSpec extends GenericJdbcJournalSpec(config = ConfigFactory.load("mysql-application.conf")) with MysqlJdbcInit

class OracleSyncJournalSpec extends GenericJdbcJournalSpec(config = ConfigFactory.load("oracle-application.conf")) with OracleJdbcInit
