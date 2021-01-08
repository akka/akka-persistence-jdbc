package akka.persistence.jdbc

import akka.{Done, NotUsed}
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
    implicit val system: ActorSystem = ActorSystem("example")

    // #read-journal
    import akka.persistence.query.PersistenceQuery
    import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal

    val readJournal: JdbcReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)
    // #read-journal
  }

  def persistenceIds(): Unit = {
    import akka.stream.scaladsl.Source
    implicit val system: ActorSystem = ActorSystem()
    // #persistence-ids
    import akka.persistence.query.PersistenceQuery
    import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal

    val readJournal: JdbcReadJournal = PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

    val willNotCompleteTheStream: Source[String, NotUsed] = readJournal.persistenceIds()

    val willCompleteTheStream: Source[String, NotUsed] = readJournal.currentPersistenceIds()
    // #persistence-ids
  }

}
