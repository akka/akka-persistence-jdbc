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

package akka.persistence.jdbc.dao

import akka.persistence.jdbc.TestSpec
import org.scalatest.Ignore
import slick.driver.PostgresDriver.api._

@Ignore
class SlickSnapshotDaoQueriesTest extends TestSpec {
  val pgQueries = new SlickSnapshotDaoQueries(slick.driver.PostgresDriver)

  it should "selectAll" in {
    forAll { (persistenceId: String) ⇒
      val q1 = pgQueries.selectAll(persistenceId).result.statements.head
      val q2 = s"""select "persistence_id", "sequence_number", "created", "snapshot" from "snapshot" where "persistence_id" = '$persistenceId'"""
      q1 shouldBe q2
    }
  }

  it should "maxSeqNrForPersistenceId" in {
    forAll { (persistenceId: String) ⇒
      val q1 = pgQueries.maxSeqNrForPersistenceId(persistenceId).result.statements.head
      val q2 = s"""select max("sequence_number") from "snapshot" where "persistence_id" = '$persistenceId'"""
      q1 shouldBe q2
    }
  }

  it should "selectByPersistenceIdAndMaxSeqNr" in {
    forAll { (persistenceId: String) ⇒
      val q1 = pgQueries.selectByPersistenceIdAndMaxSeqNr(persistenceId).result.statements.head
      val q2 = s"""select "persistence_id", "sequence_number", "created", "snapshot" from "snapshot" where ("persistence_id" = '$persistenceId') and ("sequence_number" = (select max("sequence_number") from "snapshot" where "persistence_id" = '$persistenceId'))"""
      q1 shouldBe q2
    }
  }
}
