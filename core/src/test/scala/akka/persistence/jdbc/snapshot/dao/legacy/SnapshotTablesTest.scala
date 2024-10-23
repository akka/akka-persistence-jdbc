/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.snapshot.dao.legacy

import akka.persistence.jdbc.TablesTestSpec
import slick.jdbc.JdbcProfile

class SnapshotTablesTest extends TablesTestSpec {
  val snapshotTableConfiguration = snapshotConfig.legacySnapshotTableConfiguration
  object TestByteASnapshotTables extends SnapshotTables {
    override val profile: JdbcProfile = slick.jdbc.PostgresProfile
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
    TestByteASnapshotTables.SnapshotTable.baseTableRow.persistenceId.toString shouldBe colName(
      snapshotTableConfiguration.columnNames.persistenceId)
    TestByteASnapshotTables.SnapshotTable.baseTableRow.sequenceNumber.toString shouldBe colName(
      snapshotTableConfiguration.columnNames.sequenceNumber)
    TestByteASnapshotTables.SnapshotTable.baseTableRow.created.toString shouldBe colName(
      snapshotTableConfiguration.columnNames.created)
    TestByteASnapshotTables.SnapshotTable.baseTableRow.snapshot.toString shouldBe colName(
      snapshotTableConfiguration.columnNames.snapshot)
  }
}
