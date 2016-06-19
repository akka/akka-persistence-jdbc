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

package akka.persistence.jdbc.dao.bytea.journal

import akka.persistence.jdbc.dao.TablesTestSpec
import slick.driver.JdbcProfile

class JournalTablesTest extends TablesTestSpec {

  val journalTableConfiguration = journalConfig.journalTableConfiguration
  val deletedToTableConfiguration = journalConfig.deletedToTableConfiguration

  object TestByteAJournalTables extends JournalTables {
    override val profile: JdbcProfile = slick.driver.PostgresDriver
    override val journalTableCfg = journalTableConfiguration
    override val deletedToTableCfg = deletedToTableConfiguration
  }

  "JournalTable" should "be configured with a schema name" in {
    TestByteAJournalTables.JournalTable.baseTableRow.schemaName shouldBe journalTableConfiguration.schemaName
  }

  it should "be configured with a table name" in {
    TestByteAJournalTables.JournalTable.baseTableRow.tableName shouldBe journalTableConfiguration.tableName
  }

  it should "be configured with column names" in {
    val colName = toColumnName(journalTableConfiguration.tableName)(_)
    TestByteAJournalTables.JournalTable.baseTableRow.persistenceId.toString shouldBe colName(journalTableConfiguration.columnNames.persistenceId)
    TestByteAJournalTables.JournalTable.baseTableRow.sequenceNumber.toString shouldBe colName(journalTableConfiguration.columnNames.sequenceNumber)
    TestByteAJournalTables.JournalTable.baseTableRow.created.toString shouldBe colName(journalTableConfiguration.columnNames.created)
    //    TestByteAJournalTables.JournalTable.baseTableRow.tags.toString() shouldBe colName(journalTableConfiguration.columnNames.tags)
  }

  "DeletedToTable" should "be configured with a schema name" in {
    TestByteAJournalTables.DeletedToTable.baseTableRow.schemaName shouldBe deletedToTableConfiguration.schemaName
  }

  it should "be configured with a table name" in {
    TestByteAJournalTables.DeletedToTable.baseTableRow.tableName shouldBe deletedToTableConfiguration.tableName
  }

  it should "be configured with column names" in {
    val colName = toColumnName(deletedToTableConfiguration.tableName)(_)
    TestByteAJournalTables.DeletedToTable.baseTableRow.persistenceId.toString shouldBe colName(deletedToTableConfiguration.columnNames.persistenceId)
    TestByteAJournalTables.DeletedToTable.baseTableRow.deletedTo.toString shouldBe colName(deletedToTableConfiguration.columnNames.deletedTo)
  }
}
