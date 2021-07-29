/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.state

import akka.persistence.jdbc.config.DurableStateTableConfiguration
import slick.jdbc.JdbcProfile
import slick.dbio.Effect
import slick.sql.SqlStreamingAction

private[jdbc] trait SequenceNextValUpdater {
  def getSequenceNextValueExpr(): SqlStreamingAction[Vector[String], String, Effect]
}

class H2SequenceNextValUpdater(profile: JdbcProfile, val durableStateTableCfg: DurableStateTableConfiguration) extends SequenceNextValUpdater {
  import profile.api._

  // H2 dependent (https://stackoverflow.com/questions/36244641/h2-equivalent-of-postgres-serial-or-bigserial-column)
  def getSequenceNextValueExpr() = {
    sql"""SELECT COLUMN_DEFAULT
          FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_NAME = '#${durableStateTableCfg.tableName}'
            AND COLUMN_NAME = '#${durableStateTableCfg.columnNames.globalOffset}'
            AND TABLE_SCHEMA = 'PUBLIC'""".as[String]
  }
}

class PostgresSequenceNextValUpdater(profile: JdbcProfile, val durableStateTableCfg: DurableStateTableConfiguration) extends SequenceNextValUpdater {
  import profile.api._
  final val nextValFetcher = s"""(SELECT nextval(pg_get_serial_sequence('${durableStateTableCfg.tableName}', '${durableStateTableCfg.columnNames.globalOffset}')))"""

  def getSequenceNextValueExpr() = sql"""#$nextValFetcher""".as[String]
}
