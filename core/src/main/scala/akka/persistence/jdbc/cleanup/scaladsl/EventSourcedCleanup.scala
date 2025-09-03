/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.cleanup.scaladsl

import scala.concurrent.{ ExecutionContext, Future }
import akka.Done
import akka.actor.{ ActorSystem, ClassicActorSystemProvider }
import akka.annotation.ApiMayChange
import akka.persistence.jdbc.config.{ JournalConfig, SnapshotConfig }
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.journal.dao.JournalDaoInstantiation
import akka.persistence.jdbc.snapshot.dao.SnapshotDaoInstantiation
import akka.stream.{ Materializer, SystemMaterializer }

/**
 * Scala API: Tool for deleting events and/or snapshots for a `persistenceId` without using persistent actors.
 *
 * When running an operation with `EventSourcedCleanup` that deletes all events for a persistence id, the actor with
 * that persistence id must not be running! If the actor is restarted it would in that case be recovered to the wrong
 * state since the stored events have been deleted. Delete events before snapshot can still be used while the actor is
 * running.
 *
 * If `resetSequenceNumber` is `true` then the creating entity with the same `persistenceId` will start from 0.
 * Otherwise it will continue from the latest highest used sequence number.
 *
 * WARNING: reusing the same `persistenceId` after resetting the sequence number should be avoided, since it might be
 * confusing to reuse the same sequence number for new events.
 */
@ApiMayChange
final class EventSourcedCleanup(
    systemProvider: ClassicActorSystemProvider,
    journalConfigPath: String,
    snapshotConfigPath: String) {

  def this(systemProvider: ClassicActorSystemProvider) =
    this(systemProvider, "jdbc-journal", "jdbc-snapshot-store")

  private implicit val system: ActorSystem = systemProvider.classicSystem
  private implicit val executionContext: ExecutionContext = system.dispatchers.defaultGlobalDispatcher
  private implicit val mat: Materializer = SystemMaterializer(system).materializer
  private val slick = SlickExtension(system)

  private val journalConfig = system.settings.config.getConfig(journalConfigPath)
  private val journalDao =
    JournalDaoInstantiation.journalDao(new JournalConfig(journalConfig), slick.database(journalConfig))

  private val snapshotConfig = system.settings.config.getConfig(snapshotConfigPath)
  private val snapshotDao =
    SnapshotDaoInstantiation.snapshotDao(new SnapshotConfig(snapshotConfig), slick.database(snapshotConfig))

  /**
   * Delete all events related to one single `persistenceId`. Snapshots are not deleted.
   */
  def deleteAllEvents(persistenceId: String, resetSequenceNumber: Boolean): Future[Done] = {
    journalDao.deleteEventsTo(persistenceId, toSequenceNr = Long.MaxValue, resetSequenceNumber).map(_ => Done)
  }

  /**
   * Delete snapshots related to one single `persistenceId`. Events are not deleted.
   */
  def deleteSnapshot(persistenceId: String): Future[Done] = {
    snapshotDao.deleteUpToMaxSequenceNr(persistenceId, Long.MaxValue).map(_ => Done)
  }

  /**
   * Delete everything related to one single `persistenceId`. All events and snapshots are deleted.
   */
  def deleteAll(persistenceId: String, resetSequenceNumber: Boolean): Future[Done] = {
    for {
      _ <- deleteAllEvents(persistenceId, resetSequenceNumber)
      _ <- deleteSnapshot(persistenceId)
    } yield Done
  }
}
