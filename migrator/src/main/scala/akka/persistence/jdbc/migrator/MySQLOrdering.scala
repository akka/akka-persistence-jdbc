/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.migrator

import akka.persistence.jdbc.config.JournalConfig
import akka.persistence.jdbc.journal.dao.JournalQueries
import slick.jdbc.JdbcBackend
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.{ ExecutionContext, Future }

case class MySQLOrdering(journalConfig: JournalConfig, journalQueries: JournalQueries, database: JdbcBackend.Database)(
    implicit ec: ExecutionContext)
    extends JournalOrdering {

  /**
   * helps set the next ordering value in the event_journal table
   */
  override def setVal(): Future[Unit] = {
    for {
      max <- database.run(journalQueries.JournalTable.map(_.ordering).max.get.result)
      _ <- database.run(sql"""ALTER TABLE #$tableName AUTO_INCREMENT = ${max + 1}""".as[Long].head)
    } yield ()
  }
}