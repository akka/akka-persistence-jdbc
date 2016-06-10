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
import akka.persistence.jdbc.config._
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

  "DeletedToTableConfiguration" should "be parsed with no schema name" in {
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
      DeletedToTableColumnNames(
        "persistence_id",
        "deleted_to"
      )
    )
  }

  it should "map custom column names" in {
    val config = ConfigFactory.parseString(
      """
        |akka-persistence-jdbc {
        |  tables {
        |      deletedTo {
        |      tableName = "deleted_to"
        |      schemaName = ""
        |      columnNames = {
        |        persistenceId = "pid"
        |        deletedTo = "removed_to"
        |      }
        |    }
        |  }
        |}
      """.stripMargin
    )
    DeletedToTableConfiguration(config) shouldBe DeletedToTableConfiguration(
      "deleted_to",
      None,
      DeletedToTableColumnNames(
        "pid",
        "removed_to"
      )
    )
  }

  it should "be parsed with a schemaName" in {
    val config = ConfigFactory.parseString(
      """
        |akka-persistence-jdbc {
        |  tables {
        |    deletedTo {
        |      tableName = "deleted_to"
        |      schemaName = "public"
        |      columnNames {
        |      }
        |    }
        |  }
        |}
      """.stripMargin
    )
    DeletedToTableConfiguration(config) shouldBe DeletedToTableConfiguration(
      "deleted_to",
      Some("public"),
      DeletedToTableColumnNames(
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

  it should "map custom column names" in {
    val config = ConfigFactory.parseString(
      """
        |akka-persistence-jdbc {
        |  tables {
        |    snapshot {
        |      tableName = "snapshot"
        |      schemaName = ""
        |      columnNames {
        |        persistenceId = "pid"
        |        sequenceNumber = "seqno"
        |        created = "millis_from_epoch"
        |        snapshot = "data"
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
        "pid",
        "seqno",
        "millis_from_epoch",
        "data"
      )
    )
  }

  it should "be parsed with a schemaName" in {
    val config = ConfigFactory.parseString(
      """
        |akka-persistence-jdbc {
        |  tables {
        |    snapshot {
        |      tableName = "snapshot"
        |      schemaName = "public"
        |      columnNames {
        |      }
        |    }
        |  }
        |}
      """.stripMargin
    )
    SnapshotTableConfiguration(config) shouldBe SnapshotTableConfiguration(
      "snapshot",
      Some("public"),
      SnapshotTableColumnNames(
        "persistence_id",
        "sequence_nr",
        "created",
        "snapshot"
      )
    )
  }

  "all config" should "be parsed" in {
    val config = ConfigFactory.parseString(
      """
        |akka-persistence-jdbc {
        |
        |  inMemory = off
        |  inMemoryTimeout = 5s
        |
        |  tables {
        |    journal {
        |      tableName = "journal"
        |      schemaName = "journal_schema_name"
        |      columnNames {
        |        persistenceId = "persistence_id"
        |        sequenceNumber = "sequence_number"
        |        created = "created"
        |        tags = "tags"
        |        message = "message"
        |      }
        |    }
        |
        |    deletedTo {
        |      tableName = "deleted_to"
        |      schemaName = "deleted_to_schema_name"
        |      columnNames = {
        |        persistenceId = "persistence_id"
        |        deletedTo = "deleted_to"
        |      }
        |    }
        |
        |    snapshot {
        |      tableName = "snapshot"
        |      schemaName = "snapshot_schema_name"
        |      columnNames {
        |        persistenceId = "persistence_id"
        |        sequenceNumber = "sequence_number"
        |        created = "created"
        |        snapshot = "snapshot"
        |      }
        |    }
        |  }
        |
        |  query {
        |    separator = ","
        |  }
        |
        |  serialization {
        |    journal = on // alter only when using a custom dao
        |    snapshot = on // alter only when using a custom dao
        |  }
        |
        |  dao {
        |    journal = "akka.persistence.jdbc.dao.bytea.ByteArrayJournalDao"
        |    snapshot = "akka.persistence.jdbc.dao.bytea.ByteArraySnapshotDao"
        |  }
        |
        |  serialization.varchar.serializerIdentity = 1000
        |}
      """.stripMargin
    )

    JournalTableConfiguration(config) shouldBe JournalTableConfiguration(
      tableName = "journal",
      schemaName = Some("journal_schema_name"),
      columnNames = JournalTableColumnNames(
        persistenceId = "persistence_id",
        sequenceNumber = "sequence_number",
        created = "created",
        tags = "tags",
        message = "message"
      )
    )

    DeletedToTableConfiguration(config) shouldBe DeletedToTableConfiguration(
      tableName = "deleted_to",
      schemaName = Some("deleted_to_schema_name"),
      columnNames = DeletedToTableColumnNames(
        persistenceId = "persistence_id",
        deletedTo = "deleted_to"
      )
    )

    SnapshotTableConfiguration(config) shouldBe SnapshotTableConfiguration(
      tableName = "snapshot",
      schemaName = Some("snapshot_schema_name"),
      columnNames = SnapshotTableColumnNames(
        persistenceId = "persistence_id",
        sequenceNumber = "sequence_number",
        created = "created",
        snapshot = "snapshot"
      )
    )

    SerializationConfiguration(config) shouldBe SerializationConfiguration(
      journal = true,
      snapshot = true,
      serializationIdentity = Some(1000)
    )
  }
}
