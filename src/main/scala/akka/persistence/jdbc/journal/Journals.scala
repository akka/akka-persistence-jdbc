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

import akka.persistence.jdbc.extension.ScalikeExtension
import akka.serialization.SerializationExtension
import scalikejdbc.DBSession

import scala.concurrent.ExecutionContext

trait GenericJdbcSyncWriteJournal extends JdbcAsyncWriteJournal with GenericStatements {
  implicit val session: DBSession = ScalikeExtension(context.system).session

  implicit val executionContext: ExecutionContext = context.system.dispatcher

  implicit val journalConverter = ScalikeExtension(context.system).journalConverter

  implicit val serialization = SerializationExtension(context.system)
}

class PostgresqlSyncWriteJournal extends GenericJdbcSyncWriteJournal with PostgresqlStatements

class MysqlSyncWriteJournal extends GenericJdbcSyncWriteJournal with MySqlStatements

class H2SyncWriteJournal extends GenericJdbcSyncWriteJournal with H2Statements

class OracleSyncWriteJournal extends GenericJdbcSyncWriteJournal with OracleStatements

class MSSqlServerSyncWriteJournal extends GenericJdbcSyncWriteJournal with MSSqlServerStatements

class InformixSyncWriteJournal extends GenericJdbcSyncWriteJournal with InformixStatements
