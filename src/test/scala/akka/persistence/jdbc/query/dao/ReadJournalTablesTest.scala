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

package akka.persistence.jdbc.query.dao

import akka.persistence.jdbc.TablesTestSpec
import akka.persistence.jdbc.journal.dao.JournalTables
import slick.jdbc.JdbcProfile

class ReadJournalTablesTest extends TablesTestSpec {

  val readJournalTableConfiguration = readJournalConfig.journalTableConfiguration

  object TestByteAReadJournalTables extends JournalTables {
    override val profile: JdbcProfile = slick.jdbc.PostgresProfile
    override val journalTableCfg = readJournalTableConfiguration
    override val journalTagTableCfg = readJournalConfig.journalTagTableConfiguration
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
    //    TestByteAJournalTables.JournalTable.baseTableRow.tags.toString() shouldBe colName(journalTableConfiguration.columnNames.tags)
  }
}
