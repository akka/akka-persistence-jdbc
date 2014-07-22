package akka.persistence.jdbc.util

import scalikejdbc._

trait JdbcInit {
  implicit def session: DBSession

  def createJournalTable(): Unit

  def dropJournalTable(): Unit

  def clearJournalTable(): Unit

  def createSnapshotTable(): Unit

  def dropSnapshotTable(): Unit

  def clearSnapshotTable(): Unit
}

trait GenericJdbcInit extends JdbcInit {

  def createJournalTable(): Unit = sql"""
                           CREATE TABLE IF NOT EXISTS public.journal (
                           persistence_id VARCHAR(255) NOT NULL,
                           sequence_number BIGINT NOT NULL,
                           marker VARCHAR(255) NOT NULL,
                           message TEXT NOT NULL,
                           created TIMESTAMP NOT NULL,
                           PRIMARY KEY(persistence_id, sequence_number))""".update.apply

  def dropJournalTable(): Unit = sql"DROP TABLE IF EXISTS public.journal".update.apply

  def clearJournalTable(): Unit = sql"DELETE FROM public.journal".update.apply

  def createSnapshotTable(): Unit = sql"""
                            CREATE TABLE IF NOT EXISTS public.snapshot (
                            persistence_id VARCHAR(255) NOT NULL,
                            sequence_nr BIGINT NOT NULL,
                            snapshot TEXT NOT NULL,
                            created BIGINT NOT NULL,
                            PRIMARY KEY (persistence_id, sequence_nr))""".update.apply

  def dropSnapshotTable(): Unit = sql"DROP TABLE IF EXISTS public.snapshot".update.apply

  def clearSnapshotTable(): Unit = sql"DELETE FROM public.snapshot".update.apply
}

trait PostgresqlJdbcInit extends GenericJdbcInit

trait H2JdbcInit extends GenericJdbcInit

trait MysqlJdbcInit extends GenericJdbcInit {
  override def createJournalTable(): Unit = sql"""
                           CREATE TABLE IF NOT EXISTS journal (
                           persistence_id VARCHAR(255) NOT NULL,
                           sequence_number BIGINT NOT NULL,
                           marker VARCHAR(255) NOT NULL,
                           message TEXT NOT NULL,
                           created TIMESTAMP NOT NULL,
                           PRIMARY KEY(persistence_id, sequence_number))""".update.apply

  override def dropJournalTable(): Unit = sql"DROP TABLE IF EXISTS journal".update.apply

  override def clearJournalTable(): Unit = sql"DELETE FROM journal".update.apply

  override def createSnapshotTable(): Unit = sql"""
                            CREATE TABLE IF NOT EXISTS snapshot (
                            persistence_id VARCHAR(255) NOT NULL,
                            sequence_nr BIGINT NOT NULL,
                            snapshot TEXT NOT NULL,
                            created BIGINT NOT NULL,
                            PRIMARY KEY (persistence_id, sequence_nr))""".update.apply

  override def dropSnapshotTable(): Unit = sql"DROP TABLE IF EXISTS snapshot".update.apply

  override def clearSnapshotTable(): Unit = sql"DELETE FROM snapshot".update.apply
}

trait OracleJdbcInit extends GenericJdbcInit {
  override def createJournalTable(): Unit = sql"""
                           CREATE TABLE journal (
                            persistence_id VARCHAR(255) NOT NULL,
                            sequence_number NUMERIC NOT NULL,
                            marker VARCHAR(255) NOT NULL,
                            message CLOB NOT NULL,
                            created TIMESTAMP NOT NULL,
                            PRIMARY KEY(persistence_id, sequence_number))""".update.apply

  override def dropJournalTable(): Unit = sql"DROP TABLE journal CASCADE CONSTRAINT".update.apply

  override def clearJournalTable(): Unit = sql"DELETE FROM journal".update.apply

  override def createSnapshotTable(): Unit = sql"""
                               CREATE TABLE snapshot (
                                persistence_id VARCHAR(255) NOT NULL,
                                sequence_nr NUMERIC NOT NULL,
                                snapshot CLOB NOT NULL,
                                created NUMERIC NOT NULL,
                                PRIMARY KEY (persistence_id, sequence_nr))""".update.apply

  override def dropSnapshotTable(): Unit = sql"DROP TABLE snapshot CASCADE CONSTRAINT".update.apply

  override def clearSnapshotTable(): Unit = sql"DELETE FROM snapshot".update.apply

}