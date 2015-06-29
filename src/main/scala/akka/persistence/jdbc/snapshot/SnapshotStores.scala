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

package akka.persistence.jdbc.snapshot

import akka.persistence.jdbc.extension.ScalikeExtension
import akka.serialization.SerializationExtension
import scalikejdbc.DBSession

trait GenericSyncSnapshotStore extends JdbcSyncSnapshotStore with GenericStatements {
  override implicit val session: DBSession = ScalikeExtension(system).session

  implicit val snapshotConverter = ScalikeExtension(context.system).snapshotConverter

  implicit val serialization = SerializationExtension(context.system)
}

class PostgresqlSyncSnapshotStore extends GenericSyncSnapshotStore with PostgresqlStatements

class MysqlSyncSnapshotStore extends GenericSyncSnapshotStore with MySqlStatements

class H2SyncSnapshotStore extends GenericSyncSnapshotStore with H2Statements

class OracleSyncSnapshotStore extends GenericSyncSnapshotStore with OracleStatements

class MSSqlServerSyncSnapshotStore extends GenericSyncSnapshotStore with MSSqlServerStatements

class InformixSyncSnapshotStore extends GenericSyncSnapshotStore with InformixStatements
