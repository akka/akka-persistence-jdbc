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

package akka.persistence.jdbc.snapshot.dao

import akka.persistence.jdbc.TablesTestSpec
import slick.driver.JdbcProfile

class SnapshotTablesTest extends TablesTestSpec {
  val snapshotTableConfiguration = snapshotConfig.snapshotTableConfiguration
  object TestByteASnapshotTables extends SnapshotTables {
    override val profile: JdbcProfile = slick.driver.PostgresDriver
    override val snapshotTableCfg = snapshotTableConfiguration
  }

  "SnapshotTable" should "be configured with a schema name" in {
    TestByteASnapshotTables.SnapshotTable.baseTableRow.schemaName shouldBe snapshotTableConfiguration.schemaName
  }

  it should "be configured with a table name" in {
    TestByteASnapshotTables.SnapshotTable.baseTableRow.tableName shouldBe snapshotTableConfiguration.tableName
  }

  it should "be configured with column names" in {
    val colName = toColumnName(snapshotTableConfiguration.tableName)(_)
    TestByteASnapshotTables.SnapshotTable.baseTableRow.persistenceId.toString shouldBe colName(snapshotTableConfiguration.columnNames.persistenceId)
    TestByteASnapshotTables.SnapshotTable.baseTableRow.sequenceNumber.toString shouldBe colName(snapshotTableConfiguration.columnNames.sequenceNumber)
    TestByteASnapshotTables.SnapshotTable.baseTableRow.created.toString shouldBe colName(snapshotTableConfiguration.columnNames.created)
    TestByteASnapshotTables.SnapshotTable.baseTableRow.snapshot.toString shouldBe colName(snapshotTableConfiguration.columnNames.snapshot)
  }
}
