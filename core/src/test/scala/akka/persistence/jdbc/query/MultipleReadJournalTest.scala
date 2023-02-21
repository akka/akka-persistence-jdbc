/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.query

import akka.persistence.jdbc.query.EventsByTagTest._
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.{ NoOffset, PersistenceQuery }
import akka.stream.scaladsl.Sink

class MultipleReadJournalTest
    extends QueryTestSpec("h2-two-read-journals-application.conf", configOverrides)
    with H2Cleaner {
  it should "be able to create two read journals and use eventsByTag on them" in withActorSystem { implicit system =>
    val normalReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)
    val secondReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal]("jdbc-read-journal-number-two")

    val events1 = normalReadJournal.currentEventsByTag("someTag", NoOffset).runWith(Sink.seq)
    val events2 = secondReadJournal.currentEventsByTag("someTag", NoOffset).runWith(Sink.seq)
    events1.futureValue shouldBe empty
    events2.futureValue shouldBe empty
  }
}
