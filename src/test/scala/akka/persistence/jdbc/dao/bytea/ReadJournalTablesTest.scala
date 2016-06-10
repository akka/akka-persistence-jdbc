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

package akka.persistence.jdbc.dao.bytea

import akka.persistence.jdbc.dao.TablesTestSpec
import slick.driver.JdbcProfile

class ReadJournalTablesTest extends TablesTestSpec {

  val readJournalTableConfiguration = readJournalConfig.journalTableConfiguration

  object TestByteAReadJournalTables extends ReadJournalTables {
    override val profile: JdbcProfile = slick.driver.PostgresDriver
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
    TestByteAReadJournalTables.JournalTable.baseTableRow.persistenceId.toString shouldBe colName(readJournalTableConfiguration.columnNames.persistenceId)
    TestByteAReadJournalTables.JournalTable.baseTableRow.sequenceNumber.toString shouldBe colName(readJournalTableConfiguration.columnNames.sequenceNumber)
    TestByteAReadJournalTables.JournalTable.baseTableRow.created.toString shouldBe colName(readJournalTableConfiguration.columnNames.created)
    //    TestByteAJournalTables.JournalTable.baseTableRow.tags.toString() shouldBe colName(journalTableConfiguration.columnNames.tags)
  }
}
