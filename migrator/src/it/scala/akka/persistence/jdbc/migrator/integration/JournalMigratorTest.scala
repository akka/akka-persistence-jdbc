package akka.persistence.jdbc.migrator.integration

import akka.persistence.jdbc.migrator.JournalMigratorSpec._
import akka.persistence.jdbc.migrator.JournalMigratorTest

class PostgresJournalMigratorTest extends JournalMigratorTest("postgres-application.conf") with PostgresCleaner

class MySQLJournalMigratorTest extends JournalMigratorTest("mysql-application.conf") with MysqlCleaner

class OracleJournalMigratorTest extends JournalMigratorTest("oracle-application.conf") with OracleCleaner

class SqlServerJournalMigratorTest extends JournalMigratorTest("sqlserver-application.conf") with SqlServerCleaner
