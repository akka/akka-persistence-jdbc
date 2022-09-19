/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc

import akka.{ Done, NotUsed }
import akka.actor.ActorSystem

import scala.concurrent.Future

object ScaladslSnippets {

  def create(): Unit = {
    // #create
    import akka.persistence.jdbc.testkit.scaladsl.SchemaUtils

    implicit val system: ActorSystem = ActorSystem("example")
    val done: Future[Done] = SchemaUtils.createIfNotExists()
    // #create
  }

  def readJournal(): Unit = {
    implicit val system: ActorSystem = ActorSystem()

    // #read-journal
    import akka.persistence.query.PersistenceQuery
    import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal

    val readJournal: JdbcReadJournal =
      PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)
    // #read-journal
  }

  def persistenceIds(): Unit = {
    implicit val system: ActorSystem = ActorSystem()

    // #persistence-ids
    import akka.stream.scaladsl.Source
    import akka.persistence.query.PersistenceQuery
    import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal

    val readJournal: JdbcReadJournal =
      PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

    val willNotCompleteTheStream: Source[String, NotUsed] = readJournal.persistenceIds()

    val willCompleteTheStream: Source[String, NotUsed] = readJournal.currentPersistenceIds()
    // #persistence-ids
  }

  def eventsByPersistenceId(): Unit = {
    implicit val system: ActorSystem = ActorSystem()

    // #events-by-persistence-id
    import akka.stream.scaladsl.Source
    import akka.persistence.query.{ EventEnvelope, PersistenceQuery }
    import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal

    val readJournal: JdbcReadJournal =
      PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

    val willNotCompleteTheStream: Source[EventEnvelope, NotUsed] =
      readJournal.eventsByPersistenceId("some-persistence-id", 0L, Long.MaxValue)

    val willCompleteTheStream: Source[EventEnvelope, NotUsed] =
      readJournal.currentEventsByPersistenceId("some-persistence-id", 0L, Long.MaxValue)
    // #events-by-persistence-id
  }

  def eventsByTag(): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    // #events-by-tag
    import akka.stream.scaladsl.Source
    import akka.persistence.query.{ EventEnvelope, PersistenceQuery }
    import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal

    val readJournal: JdbcReadJournal =
      PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

    val willNotCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.eventsByTag("apple", 0L)

    val willCompleteTheStream: Source[EventEnvelope, NotUsed] = readJournal.currentEventsByTag("apple", 0L)
    // #events-by-tag
  }
}
