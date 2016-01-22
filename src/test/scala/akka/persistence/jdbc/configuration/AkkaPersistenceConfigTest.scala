/*
 * Copyright 2015 Dennis Vriend
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

import akka.persistence.jdbc.extension.{ SnapshotTableConfiguration, JournalTableConfiguration }
import com.typesafe.config.ConfigFactory
import org.scalatest.{ FlatSpec, Matchers }

class AkkaPersistenceConfigTest extends FlatSpec with Matchers {

  "JournalTableConfig" should "be parsed" in {
    val config = ConfigFactory.parseString(
      """
        |akka-persistence-jdbc {
        |  tables {
        |    journal {
        |      tableName = "journal"
        |      schemaName = ""
        |    }
        |  }
        |}
      """.stripMargin
    )
    JournalTableConfiguration().fromConfig(config) shouldBe JournalTableConfiguration("journal", None)
  }

  "SlickTableConfig" should "be parsed" in {
    val config = ConfigFactory.parseString(
      """
        |akka-persistence-jdbc {
        |  tables {
        |    snapshot {
        |      tableName = "snapshot"
        |      schemaName = ""
        |    }
        |  }
        |}
      """.stripMargin
    )
    SnapshotTableConfiguration().fromConfig(config) shouldBe SnapshotTableConfiguration("snapshot", None)
  }
}
