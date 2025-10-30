/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2025 Lightbend Inc. <https://akka.io>
 */

package akka.persistence.jdbc.cleanup.javadsl

import java.util.concurrent.CompletionStage
import scala.jdk.FutureConverters._

import akka.Done
import akka.actor.ClassicActorSystemProvider
import akka.annotation.ApiMayChange
import akka.persistence.jdbc.cleanup.scaladsl

/**
 * Java API: Tool for deleting events and/or snapshots for a `persistenceId` without using persistent actors.
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
final class EventSourcedCleanup private (delegate: scaladsl.EventSourcedCleanup) {

  def this(systemProvider: ClassicActorSystemProvider, journalConfigPath: String, snapshotConfigPath: String) =
    this(new scaladsl.EventSourcedCleanup(systemProvider, journalConfigPath, snapshotConfigPath))

  def this(systemProvider: ClassicActorSystemProvider) =
    this(systemProvider, "jdbc-journal", "jdbc-snapshot-store")

  /**
   * Delete all events related to one single `persistenceId`. Snapshots are not deleted.
   */
  def deleteAllEvents(persistenceId: String, resetSequenceNumber: Boolean): CompletionStage[Done] =
    delegate.deleteAllEvents(persistenceId, resetSequenceNumber).asJava

  /**
   * Delete snapshots related to one single `persistenceId`. Events are not deleted.
   */
  def deleteSnapshot(persistenceId: String): CompletionStage[Done] =
    delegate.deleteSnapshot(persistenceId).asJava

  /**
   * Delete everything related to one single `persistenceId`. All events and snapshots are deleted.
   */
  def deleteAll(persistenceId: String, resetSequenceNumber: Boolean): CompletionStage[Done] =
    delegate.deleteAll(persistenceId, resetSequenceNumber).asJava

}
