package akka.persistence.jdbc.state

import slick.jdbc.JdbcProfile
import slick.dbio.Effect
import slick.sql.SqlStreamingAction

trait SequenceNextValUpdater {
  def nextValUpdater(sequenceName: String): String
  def _getSequenceName(): SqlStreamingAction[Vector[String], String, Effect]
}

class H2SequenceNextValUpdater(profile: JdbcProfile) extends SequenceNextValUpdater {
  import profile.api._
  def nextValUpdater(sequenceName: String): String = {
    val sanitized = sanitizeSequenceName(sequenceName)
    s"NEXT VALUE FOR PUBLIC.${sanitized}"
  }

  // H2 dependent (https://stackoverflow.com/questions/36244641/h2-equivalent-of-postgres-serial-or-bigserial-column)
  def _getSequenceName() = {
    sql"""SELECT COLUMN_DEFAULT
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = 'state'
            AND COLUMN_NAME = 'global_offset'
            AND TABLE_SCHEMA = 'PUBLIC'""".as[String]
  }

  // H2 dependent (https://stackoverflow.com/questions/36244641/h2-equivalent-of-postgres-serial-or-bigserial-column)
  private def sanitizeSequenceName(name: String) = name.split('.')(1).stripSuffix(")")
}

class PostgresSequenceNextValUpdater(profile: JdbcProfile) extends SequenceNextValUpdater {
  import profile.api._
  def nextValUpdater(sequenceName: String): String = {
    s"(SELECT setval(pg_get_serial_sequence('state', 'global_offset'), coalesce(max(global_offset),0) + 1, false) FROM state)"
  }

  def _getSequenceName() = {
    sql"""SELECT pg_get_serial_sequence('state', 'global_offset')""".as[String]
  }
}
