/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.state

import slick.jdbc.JdbcProfile
import slick.dbio.Effect
import slick.sql.SqlStreamingAction

private[jdbc] trait SequenceNextValUpdater {
  def getSequenceNextValueExpr(): SqlStreamingAction[Vector[String], String, Effect]
}

class H2SequenceNextValUpdater(profile: JdbcProfile) extends SequenceNextValUpdater {
  import profile.api._

  // H2 dependent (https://stackoverflow.com/questions/36244641/h2-equivalent-of-postgres-serial-or-bigserial-column)
  def getSequenceNextValueExpr() = {
    sql"""SELECT COLUMN_DEFAULT
          FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_NAME = 'state'
            AND COLUMN_NAME = 'global_offset'
            AND TABLE_SCHEMA = 'PUBLIC'""".as[String]
  }
}

class PostgresSequenceNextValUpdater(profile: JdbcProfile) extends SequenceNextValUpdater {
  import profile.api._
  final val nextValFetcher = s"""(SELECT nextval(pg_get_serial_sequence('state', 'global_offset')))"""

  def getSequenceNextValueExpr() = sql"""#$nextValFetcher""".as[String]
}
