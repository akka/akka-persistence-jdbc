/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.migrator

import akka.{ Done, NotUsed }
import akka.actor.ActorSystem
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.persistence.jdbc.config.{ JournalConfig, ReadJournalConfig }
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.journal.dao.DefaultJournalDao
import akka.persistence.jdbc.journal.dao.legacy.ByteArrayJournalSerializer
import akka.persistence.jdbc.query.dao.legacy.ReadJournalQueries
import akka.persistence.journal.Tagged
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.{ Sink, Source }
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.Future
import scala.util.{ Failure, Success }

/**
 * This will help migrate the legacy journal data onto the new journal schema with the
 * appropriate serialization
 *
 * @param system the actor system
 */
final case class LegacyJournalDataMigrator()(implicit system: ActorSystem) {

  import system.dispatcher

  // get the Jdbc Profile
  protected val profile: JdbcProfile = DatabaseConfig.forConfig[JdbcProfile]("slick").profile

  import profile.api._

  // get the various configurations
  private val journalConfig = new JournalConfig(system.settings.config.getConfig("jdbc-journal"))
  private val readJournalConfig = new ReadJournalConfig(system.settings.config.getConfig("jdbc-read-journal"))

  // the journal database
  private val journaldb =
    SlickExtension(system).database(system.settings.config.getConfig("jdbc-read-journal")).database

  // get an instance of the default journal dao
  private val defaultJournalDao: DefaultJournalDao =
    new DefaultJournalDao(journaldb, profile, journalConfig, SerializationExtension(system))

  // let us get the journal reader
  private val serialization = SerializationExtension(system)
  private val journalQueries = new ReadJournalQueries(profile, readJournalConfig)
  private val serializer = new ByteArrayJournalSerializer(serialization, readJournalConfig.pluginConfig.tagSeparator)

  /**
   * reads all the current events in the legacy journal
   *
   * @return
   */
  private def allEvents: Source[PersistentRepr, NotUsed] = {
    Source
      .fromPublisher(journaldb.stream(journalQueries.JournalTable.sortBy(_.sequenceNumber).result))
      .via(serializer.deserializeFlow)
      .map {
        case Failure(exception) => throw exception
        case Success((repr, tags, _)) if tags.nonEmpty =>
          repr.withPayload(Tagged(repr, tags)) // only wrap in `Tagged` if needed
        case Success((repr, _, _)) => repr
      }

  }

  /**
   * write all legacy events into the new journal tables applying the proper serialization
   */
  def migrate(): Future[Done] = {
    allEvents
      .map(repr => {
        defaultJournalDao.asyncWriteMessages(Seq(AtomicWrite(Seq(repr))))
      })
      .runWith(Sink.ignore)
  }
}
