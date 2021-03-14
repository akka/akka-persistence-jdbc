/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.migrator

import akka.persistence.jdbc.config.JournalConfig
import akka.persistence.jdbc.journal.dao.JournalQueries
import slick.jdbc.JdbcBackend

import scala.concurrent.Future

trait JournalOrdering {
  val journalConfig: JournalConfig
  val journalQueries: JournalQueries
  val database: JdbcBackend.Database

  /**
   * the actual event journal table
   */
  val tableName: String = s"${journalConfig.eventJournalTableConfiguration.tableName}"

  /**
   * helps set the next ordering value in the event_journal table
   */
  def setVal(): Future[Unit]
}
