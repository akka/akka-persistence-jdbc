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

package akka.persistence.jdbc.configuration

import akka.persistence.jdbc.TestSpec
import akka.persistence.jdbc.extension._
import com.typesafe.config.ConfigFactory

class AkkaPersistenceConfigTest extends TestSpec {

  "JournalTableConfig" should "be parsed with no schemaName" in {
    val config = ConfigFactory.parseString(
      """
          |akka-persistence-jdbc {
          |  tables {
          |    journal {
          |      tableName = "journal"
          |      schemaName = ""
          |      columnNames {
          |      }
          |    }
          |  }
          |}
        """.stripMargin
    )
    JournalTableConfiguration(config) shouldBe JournalTableConfiguration(
      "journal",
      None,
      JournalTableColumnNames("persistence_id", "sequence_nr", "created", "tags", "message")
    )
  }

  it should "map custom column names" in {
    val config = ConfigFactory.parseString(
      """
        |akka-persistence-jdbc {
        |  tables {
        |    journal {
        |      tableName = "events"
        |      schemaName = ""
        |      columnNames {
        |       persistenceId = "pid"
        |       sequenceNumber = "seqno"
        |       created = "millis_from_epoch"
        |       tags = "event_tags"
        |       message = "event"
        |      }
        |    }
        |  }
        |}
      """.stripMargin
    )
    JournalTableConfiguration(config) shouldBe JournalTableConfiguration(
      "events",
      None,
      JournalTableColumnNames("pid", "seqno", "millis_from_epoch", "event_tags", "event")
    )
  }

  it should "be parsed with a schemaName" in {
    val config = ConfigFactory.parseString(
      """
          |akka-persistence-jdbc {
          |  tables {
          |    journal {
          |      tableName = "journal"
          |      schemaName = "public"
          |      columnNames {
          |      }
          |    }
          |  }
          |}
        """.stripMargin
    )
    JournalTableConfiguration(config) shouldBe JournalTableConfiguration(
      "journal",
      Some("public"),
      JournalTableColumnNames("persistence_id", "sequence_nr", "created", "tags", "message")
    )
  }

  "DeletedToTableConfiguration" should "be parsed" in {
    val config = ConfigFactory.parseString(
      """
        |akka-persistence-jdbc {
        |  tables {
        |    deletedTo {
        |      tableName = "deleted_to"
        |      schemaName = ""
        |      columnNames {
        |      }
        |    }
        |  }
        |}
      """.stripMargin
    )
    DeletedToTableConfiguration(config) shouldBe DeletedToTableConfiguration(
      "deleted_to",
      None,
      DeletedToColumnNames(
        "persistence_id",
        "deleted_to"
      )
    )
  }

  "SnapshotTableConfiguration" should "be parsed with no schemaName" in {
    val config = ConfigFactory.parseString(
      """
          |akka-persistence-jdbc {
          |  tables {
          |    snapshot {
          |      tableName = "snapshot"
          |      schemaName = ""
          |      columnNames {
          |      }
          |    }
          |  }
          |}
        """.stripMargin
    )
    SnapshotTableConfiguration(config) shouldBe SnapshotTableConfiguration(
      "snapshot",
      None,
      SnapshotTableColumnNames(
        "persistence_id",
        "sequence_nr",
        "created",
        "snapshot"
      )
    )
  }
}
