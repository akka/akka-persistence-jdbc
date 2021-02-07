/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.tools.migration

import akka.NotUsed
import akka.actor.ActorSystem
import akka.persistence.{ AtomicWrite, Persistence, PersistentRepr }
import akka.persistence.journal.EventAdapter
import akka.stream.scaladsl.Source
import com.typesafe.config.Config

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.Try

/**
 * This will help migrate the legacy journal data onto the new journal schema with the
 * appropriate serialization
 *
 * @param config the application config
 * @param system the actor system
 */
final case class LegacyJournalDataMigrator(config: Config)(implicit system: ActorSystem) extends DataMigrator(config) {

  private val eventAdapters = Persistence(system).adaptersFor("", config)

  private def adaptEvents(repr: PersistentRepr): Seq[PersistentRepr] = {
    val adapter: EventAdapter = eventAdapters.get(repr.payload.getClass)
    adapter.fromJournal(repr.payload, repr.manifest).events.map(repr.withPayload)
  }

  /**
   * reads all the current events in the legacy journal
   *
   * @return the source of all the events
   */
  private def allEvents(): Source[PersistentRepr, NotUsed] = {
    legacyReadJournalDao
      .allPersistenceIdsSource(Long.MaxValue)
      .flatMapConcat((persistenceId: String) => {
        legacyReadJournalDao
          .messagesWithBatch(persistenceId, 0L, Long.MaxValue, readJournalConfig.maxBufferSize, None)
          .mapAsync(1)(reprAndOrdNr => Future.fromTry(reprAndOrdNr))
          .mapConcat { case (repr, _) =>
            adaptEvents(repr)
          }
      })
  }

  /**
   * write all legacy events into the new journal tables applying the proper serialization
   */
  def migrate(): Source[Seq[Try[Unit]], NotUsed] = {
    allEvents().mapAsync(1) { pr =>
      defaultJournalDao.asyncWriteMessages(immutable.Seq(AtomicWrite(pr)))
    }
  }
}
