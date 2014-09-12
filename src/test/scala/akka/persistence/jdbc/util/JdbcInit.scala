package akka.persistence.jdbc.util

import akka.persistence.jdbc.common.PluginConfig
import scalikejdbc._

import scala.util.Try

trait JdbcInit {
  def createJournalTable(): Unit

  def dropJournalTable(): Unit

  def clearJournalTable(): Unit

  def createSnapshotTable(): Unit

  def dropSnapshotTable(): Unit

  def clearSnapshotTable(): Unit
}

trait GenericJdbcInit extends JdbcInit {
  val cfg: PluginConfig
  implicit val session: DBSession

  def createJournalTable(): Unit = SQL( s"""
                           CREATE TABLE IF NOT EXISTS ${cfg.journalSchemaName}${cfg.journalTableName} (
                           persistence_id VARCHAR(255) NOT NULL,
                           sequence_number BIGINT NOT NULL,
                           marker VARCHAR(255) NOT NULL,
                           message TEXT NOT NULL,
                           created TIMESTAMP NOT NULL,
                           PRIMARY KEY(persistence_id, sequence_number))""").update().apply

  def dropJournalTable(): Unit = SQL(s"DROP TABLE IF EXISTS ${cfg.journalSchemaName}${cfg.journalTableName}").update().apply

  def clearJournalTable(): Unit = SQL(s"DELETE FROM ${cfg.journalSchemaName}${cfg.journalTableName}").update().apply

  def createSnapshotTable(): Unit = SQL( s"""
                            CREATE TABLE IF NOT EXISTS ${cfg.snapshotSchemaName}${cfg.snapshotTableName} (
                            persistence_id VARCHAR(255) NOT NULL,
                            sequence_nr BIGINT NOT NULL,
                            snapshot TEXT NOT NULL,
                            created BIGINT NOT NULL,
                            PRIMARY KEY (persistence_id, sequence_nr))""").update().apply

  def dropSnapshotTable(): Unit = SQL(s"DROP TABLE IF EXISTS ${cfg.snapshotSchemaName}${cfg.snapshotTableName} ").update().apply

  def clearSnapshotTable(): Unit = SQL(s"DELETE FROM ${cfg.snapshotSchemaName}${cfg.snapshotTableName} ").update().apply
}

trait PostgresqlJdbcInit extends GenericJdbcInit

trait H2JdbcInit extends GenericJdbcInit

trait MysqlJdbcInit extends GenericJdbcInit {
  override def createJournalTable(): Unit = SQL(s"""
                           CREATE TABLE IF NOT EXISTS ${cfg.journalSchemaName}${cfg.journalTableName} (
                           persistence_id VARCHAR(255) NOT NULL,
                           sequence_number BIGINT NOT NULL,
                           marker VARCHAR(255) NOT NULL,
                           message TEXT NOT NULL,
                           created TIMESTAMP NOT NULL,
                           PRIMARY KEY(persistence_id, sequence_number))""").update().apply

  override def dropJournalTable(): Unit = SQL(s"DROP TABLE IF EXISTS ${cfg.journalSchemaName}${cfg.journalTableName}").update().apply

  override def clearJournalTable(): Unit = SQL(s"DELETE FROM ${cfg.journalSchemaName}${cfg.journalTableName}").update().apply

  override def createSnapshotTable(): Unit = SQL(s"""
                            CREATE TABLE IF NOT EXISTS ${cfg.snapshotSchemaName}${cfg.snapshotTableName}  (
                            persistence_id VARCHAR(255) NOT NULL,
                            sequence_nr BIGINT NOT NULL,
                            snapshot TEXT NOT NULL,
                            created BIGINT NOT NULL,
                            PRIMARY KEY (persistence_id, sequence_nr))""").update().apply

  override def dropSnapshotTable(): Unit = SQL(s"DROP TABLE IF EXISTS ${cfg.snapshotSchemaName}${cfg.snapshotTableName} ").update().apply

  override def clearSnapshotTable(): Unit = SQL(s"DELETE FROM ${cfg.snapshotSchemaName}${cfg.snapshotTableName} ").update().apply
}

trait OracleJdbcInit extends GenericJdbcInit {
  override def createJournalTable(): Unit = SQL(s"""
                           CREATE TABLE ${cfg.journalSchemaName}${cfg.journalTableName} (
                            persistence_id VARCHAR(255) NOT NULL,
                            sequence_number NUMERIC NOT NULL,
                            marker VARCHAR(255) NOT NULL,
                            message CLOB NOT NULL,
                            created TIMESTAMP NOT NULL,
                            PRIMARY KEY(persistence_id, sequence_number))""").update().apply

  override def dropJournalTable(): Unit = SQL(s"DROP TABLE ${cfg.journalSchemaName}${cfg.journalTableName} CASCADE CONSTRAINT").update().apply

  override def clearJournalTable(): Unit = SQL(s"DELETE FROM ${cfg.journalSchemaName}${cfg.journalTableName}").update().apply

  override def createSnapshotTable(): Unit = SQL(s"""
                               CREATE TABLE ${cfg.snapshotSchemaName}${cfg.snapshotTableName}  (
                                persistence_id VARCHAR(255) NOT NULL,
                                sequence_nr NUMERIC NOT NULL,
                                snapshot CLOB NOT NULL,
                                created NUMERIC NOT NULL,
                                PRIMARY KEY (persistence_id, sequence_nr))""").update().apply

  override def dropSnapshotTable(): Unit = SQL(s"DROP TABLE ${cfg.snapshotSchemaName}${cfg.snapshotTableName}  CASCADE CONSTRAINT").update().apply

  override def clearSnapshotTable(): Unit = SQL(s"DELETE FROM ${cfg.snapshotSchemaName}${cfg.snapshotTableName} ").update().apply
}

trait InformixJdbcInit extends GenericJdbcInit {
  override def createJournalTable(): Unit = SQL(s"""
                                CREATE TABLE IF NOT EXISTS ${cfg.journalSchemaName}${cfg.journalTableName} (
                                 persistence_id VARCHAR(255) NOT NULL,
                                 sequence_number NUMERIC NOT NULL,
                                 marker VARCHAR(255) NOT NULL,
                                 message CLOB NOT NULL,
                                 created DATETIME YEAR TO FRACTION(5) NOT NULL,
                                PRIMARY KEY(persistence_id, sequence_number))""").update().apply

  override def dropJournalTable(): Unit =
    Try(SQL(s"DROP TABLE ${cfg.journalSchemaName}${cfg.journalTableName}").update().apply).recover {
      case ex: Exception => createJournalTable()
    }

  override def clearJournalTable(): Unit = SQL(s"DELETE FROM ${cfg.journalSchemaName}${cfg.journalTableName}").update().apply

  override def createSnapshotTable(): Unit = SQL( s"""
                                CREATE TABLE IF NOT EXISTS ${cfg.snapshotSchemaName}${cfg.snapshotTableName}  (
                                 persistence_id VARCHAR(255) NOT NULL,
                                 sequence_nr NUMERIC NOT NULL,
                                 snapshot CLOB NOT NULL,
                                 created NUMERIC NOT NULL,
                                PRIMARY KEY (persistence_id, sequence_nr))""").update().apply

  override def dropSnapshotTable(): Unit =
    Try(SQL(s"DROP TABLE ${cfg.snapshotSchemaName}${cfg.snapshotTableName}").update().apply).recover {
      case ex: Exception => createSnapshotTable()
    }

  override def clearSnapshotTable(): Unit = SQL(s"DELETE FROM ${cfg.snapshotSchemaName}${cfg.snapshotTableName} ").update().apply
}