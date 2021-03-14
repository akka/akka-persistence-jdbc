/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.migrator

import akka.persistence.jdbc.config.JournalConfig
import akka.persistence.jdbc.journal.dao.JournalQueries
import slick.jdbc.JdbcBackend
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ ExecutionContext, Future }

case class PostgresOrdering(
    journalConfig: JournalConfig,
    journalQueries: JournalQueries,
    database: JdbcBackend.Database)(implicit ec: ExecutionContext)
    extends JournalOrdering {

  /**
   * helps set the next ordering value in the event_journal table
   */
  def setVal(): Future[Unit] = {

    for {
      max <- database.run(journalQueries.JournalTable.map(_.ordering).max.get.result)
      sequenceName: String <- database.run(
        sql"""SELECT pg_get_serial_sequence($tableName, 'ordering')""".as[String].head)
      _ <- database.run(sql""" SELECT pg_catalog.setval($sequenceName, ${max + 1}, false)""".as[Long].head)
    } yield ()
  }
}
