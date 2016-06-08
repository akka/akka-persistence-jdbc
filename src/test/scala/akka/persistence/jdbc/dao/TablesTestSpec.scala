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
import akka.persistence.jdbc.extension.{ DeletedToTableConfiguration, JournalTableConfiguration, SnapshotTableConfiguration }
import com.typesafe.config.ConfigFactory

class TablesTestSpec extends TestSpec {

  def toColumnName[A](tableName: String)(columnName: String): String = s"$tableName.$columnName"

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

  val snapshotTableConfiguration = SnapshotTableConfiguration(config)
  val journalTableConfiguration = JournalTableConfiguration(config)
  val deletedToTableConfiguration = DeletedToTableConfiguration(config)
}
