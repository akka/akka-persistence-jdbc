package akka.persistence.jdbc.integration

import akka.persistence.jdbc.journal.JdbcJournalPerfSpec
import akka.persistence.jdbc.util.Schema.{MySQL, Oracle, Postgres, SqlServer}
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}

class PostgresJournalPerfSpec extends JdbcJournalPerfSpec(ConfigFactory.load("postgres-application.conf"), Postgres()) {
  override def eventsCount: Int = 100
}

class PostgresJournalPerfSpecSharedDb
  extends JdbcJournalPerfSpec(ConfigFactory.load("postgres-shared-db-application.conf"), Postgres()) {
  override def eventsCount: Int = 100
}

class PostgresJournalPerfSpecPhysicalDelete extends PostgresJournalPerfSpec {
  this.cfg.withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false))
}

class MySQLJournalPerfSpec extends JdbcJournalPerfSpec(ConfigFactory.load("mysql-application.conf"), MySQL()) {
  override def eventsCount: Int = 100
}

class MySQLJournalPerfSpecSharedDb
  extends JdbcJournalPerfSpec(ConfigFactory.load("mysql-shared-db-application.conf"), MySQL()) {
  override def eventsCount: Int = 100
}

class MySQLJournalPerfSpecPhysicalDelete extends MySQLJournalPerfSpec {
  this.cfg.withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false))
}

class OracleJournalPerfSpec extends JdbcJournalPerfSpec(ConfigFactory.load("oracle-application.conf"), Oracle()) {
  override def eventsCount: Int = 100
}

class OracleJournalPerfSpecSharedDb
  extends JdbcJournalPerfSpec(ConfigFactory.load("oracle-shared-db-application.conf"), Oracle()) {
  override def eventsCount: Int = 100
}

class OracleJournalPerfSpecPhysicalDelete extends OracleJournalPerfSpec {
  this.cfg.withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false))
}

class SqlServerJournalPerfSpec
  extends JdbcJournalPerfSpec(ConfigFactory.load("sqlserver-application.conf"), SqlServer()) {
  override def eventsCount: Int = 100
}

class SqlServerJournalPerfSpecSharedDb
  extends JdbcJournalPerfSpec(ConfigFactory.load("sqlserver-shared-db-application.conf"), SqlServer()) {
  override def eventsCount: Int = 100
}

class SqlServerJournalPerfSpecPhysicalDelete extends SqlServerJournalPerfSpec {
  this.cfg.withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false))
}
