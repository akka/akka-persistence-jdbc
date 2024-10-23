/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.state

import akka.annotation.InternalApi
import akka.persistence.jdbc.config.DurableStateTableConfiguration
import slick.jdbc.JdbcProfile
import slick.dbio.Effect
import slick.sql.SqlStreamingAction

/**
 * INTERNAL API
 */
@InternalApi private[jdbc] trait SequenceNextValUpdater {
  def getSequenceNextValueExpr(): SqlStreamingAction[Vector[String], String, Effect]
}

/**
 * INTERNAL API
 */
@InternalApi private[jdbc] class H2SequenceNextValUpdater(
    profile: JdbcProfile,
    val durableStateTableCfg: DurableStateTableConfiguration)
    extends SequenceNextValUpdater {
  import profile.api._

  // H2 dependent (https://stackoverflow.com/questions/36244641/h2-equivalent-of-postgres-serial-or-bigserial-column)
  def getSequenceNextValueExpr() = {
    sql"""SELECT COLUMN_DEFAULT
          FROM INFORMATION_SCHEMA.COLUMNS
          WHERE TABLE_NAME = '#${durableStateTableCfg.tableName}'
            AND COLUMN_NAME = '#${durableStateTableCfg.columnNames.globalOffset}'
            AND TABLE_SCHEMA = '#${durableStateTableCfg.schemaName.getOrElse("PUBLIC")}'""".as[String]
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[jdbc] class PostgresSequenceNextValUpdater(
    profile: JdbcProfile,
    val durableStateTableCfg: DurableStateTableConfiguration)
    extends SequenceNextValUpdater {
  import profile.api._
  private val schemaPrefix = durableStateTableCfg.schemaName.map(n => s"$n.").getOrElse("")
  final val nextValFetcher =
    s"""(SELECT nextval(pg_get_serial_sequence('$schemaPrefix${durableStateTableCfg.tableName}', '${durableStateTableCfg.columnNames.globalOffset}')))"""

  def getSequenceNextValueExpr() = sql"""#$nextValFetcher""".as[String]
}
