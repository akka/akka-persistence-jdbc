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

package akka.persistence.jdbc.query

import akka.persistence.jdbc.query.EventsByTagTest._
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.{ NoOffset, PersistenceQuery }
import akka.stream.{ ActorMaterializer, Materializer }
import akka.stream.scaladsl.Sink

class MultipleReadJournalTest extends QueryTestSpec("h2-two-read-journals-application.conf", configOverrides) with H2Cleaner {

  it should "be able to create two read journals and use eventsByTag on them" in withActorSystem { implicit system =>
    implicit val mat: Materializer = ActorMaterializer()
    val normalReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)
    val secondReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal]("jdbc-read-journal-number-two")

    val events1 = normalReadJournal.currentEventsByTag("someTag", NoOffset).runWith(Sink.seq)
    val events2 = secondReadJournal.currentEventsByTag("someTag", NoOffset).runWith(Sink.seq)
    events1.futureValue shouldBe empty
    events2.futureValue shouldBe empty
  }
}
