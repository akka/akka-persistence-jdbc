/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.migrator

import akka.NotUsed
import akka.actor.ActorSystem
import akka.persistence.{ AtomicWrite, Persistence, PersistentRepr }
import akka.persistence.jdbc.config.{ JournalConfig, ReadJournalConfig }
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.journal.dao.DefaultJournalDao
import akka.persistence.jdbc.journal.dao.legacy.ByteArrayJournalSerializer
import akka.persistence.jdbc.query.dao.legacy.ReadJournalQueries
import akka.persistence.journal.{ EventAdapter, Tagged }
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.{ Sink, Source }
import com.typesafe.config.Config
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.Future
import scala.util.Try

/**
 * This will help migrate the legacy journal data onto the new journal schema with the
 * appropriate serialization
 *
 * @param config the application config
 * @param system the actor system
 */
final case class LegacyJournalDataMigrator(config: Config)(implicit system: ActorSystem) {

  import system.dispatcher

  // get the Jdbc Profile
  protected val profile: JdbcProfile = DatabaseConfig.forConfig[JdbcProfile]("slick").profile

  import profile.api._

  private val eventAdapters = Persistence(system).adaptersFor("jdbc-journal", config)

  private def adaptEvents(repr: PersistentRepr): Seq[PersistentRepr] = {
    val adapter: EventAdapter = eventAdapters.get(repr.payload.getClass)
    adapter.fromJournal(repr.payload, repr.manifest).events.map(repr.withPayload)
  }

  // get the various configurations
  private val journalConfig = new JournalConfig(config.getConfig("jdbc-journal"))
  private val readJournalConfig = new ReadJournalConfig(config.getConfig("jdbc-read-journal"))

  // the journal database
  private val journaldb = SlickExtension(system).database(config.getConfig("jdbc-read-journal")).database

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
  private def events(): Source[PersistentRepr, NotUsed] = {
    Source
      .fromPublisher(journaldb.stream(journalQueries.JournalTable.sortBy(_.sequenceNumber).result))
      .via(serializer.deserializeFlow)
      .mapAsync(1)((reprAndOrdNr: Try[(PersistentRepr, Set[String], Long)]) => Future.fromTry(reprAndOrdNr))
      .map { case (repr, tags, _) =>
        repr.withPayload(Tagged(repr.payload, tags))
      }
  }

  /**
   * write all legacy events into the new journal tables applying the proper serialization
   */
  def migrate(): Unit = {
    events()
      .mapAsync(1)((repr: PersistentRepr) => {
        defaultJournalDao.asyncWriteMessages(Seq(AtomicWrite(Seq(repr))))
      })
      .limit(Long.MaxValue)
      .runWith(Sink.seq) // FIXME for performance
      .map(_ => ())
  }
}
