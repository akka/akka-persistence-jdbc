/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.journal.dao

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

import akka.NotUsed
import akka.actor.Scheduler
import akka.persistence.PersistentRepr
import akka.stream.scaladsl.Source

trait JournalDaoWithReadMessages {

  /**
   * Returns a Source of PersistentRepr for a certain persistenceId
   */
  def messages(
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long,
      max: Long): Source[Try[PersistentRepr], NotUsed]

  /**
   * Returns a Source of PersistentRepr for a certain persistenceId
   */
  def messagesWithBatch(
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long,
      batchSize: Int,
      refreshInterval: Option[(FiniteDuration, Scheduler)]): Source[Try[PersistentRepr], NotUsed]

}
