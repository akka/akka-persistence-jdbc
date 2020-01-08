/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.query.dao

import akka.persistence.jdbc.TablesTestSpec
import akka.persistence.jdbc.journal.dao.JournalTables
import slick.jdbc.JdbcProfile

class ReadJournalTablesTest extends TablesTestSpec {
  val readJournalTableConfiguration = readJournalConfig.journalTableConfiguration

  object TestByteAReadJournalTables extends JournalTables {
    override val profile: JdbcProfile = slick.jdbc.PostgresProfile
    override val journalTableCfg = readJournalTableConfiguration
  }

  "JournalTable" should "be configured with a schema name" in {
    TestByteAReadJournalTables.JournalTable.baseTableRow.schemaName shouldBe readJournalTableConfiguration.schemaName
  }

  it should "be configured with a table name" in {
    TestByteAReadJournalTables.JournalTable.baseTableRow.tableName shouldBe readJournalTableConfiguration.tableName
  }

  it should "be configured with column names" in {
    val colName = toColumnName(readJournalTableConfiguration.tableName)(_)
    TestByteAReadJournalTables.JournalTable.baseTableRow.persistenceId.toString shouldBe colName(
      readJournalTableConfiguration.columnNames.persistenceId)
    TestByteAReadJournalTables.JournalTable.baseTableRow.sequenceNumber.toString shouldBe colName(
      readJournalTableConfiguration.columnNames.sequenceNumber)
    //    TestByteAJournalTables.JournalTable.baseTableRow.tags.toString() shouldBe colName(journalTableConfiguration.columnNames.tags)
  }
}
