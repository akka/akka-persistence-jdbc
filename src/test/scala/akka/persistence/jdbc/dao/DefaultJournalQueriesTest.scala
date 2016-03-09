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

package akka.persistence.jdbc.dao

import akka.persistence.jdbc.TestSpec
import akka.persistence.jdbc.extension.{ DeletedToTableColumnNames, DeletedToTableConfiguration, JournalTableColumnNames, JournalTableConfiguration }

class DefaultJournalQueriesTest extends TestSpec {
  val postgresJournalQueries = new DefaultJournalQueries(
    slick.driver.PostgresDriver,
    JournalTableConfiguration(
      tableName = "journal",
      schemaName = None,
      JournalTableColumnNames(
        persistenceId = "persistence_id",
        sequenceNumber = "sequence_nr",
        created = "created",
        tags = "tags",
        message = "message"
      )
    ),
    DeletedToTableConfiguration(
      tableName = "deletedTo",
      schemaName = None,
      DeletedToTableColumnNames(
        persistenceId = "persistence_id",
        deletedTo = "deleted_to"
      )
    )
  )

  "eventsByTag" should "escape non valid tag characters" in {
    import slick.driver.PostgresDriver.api._
    val statements = postgresJournalQueries.eventsByTag("one", 0).result.statements
    statements should not be 'empty
    statements.head shouldBe """select "persistence_id", "sequence_nr", "message", "created", "tags" from "journal" where "tags" like '%one%' order by "created" offset 0"""
  }
}
