package akka.persistence.jdbc.migrator.integration

import akka.persistence.jdbc.migrator.{JournalMigratorSpec, JournalMigratorTest}

class PostgresJournalMigratorTest
    extends JournalMigratorTest("postgres-application.conf")
    with JournalMigratorSpec.PostgresCleaner

class MySQLJournalMigratorTest
    extends JournalMigratorTest("mysql-application.conf")
    with JournalMigratorSpec.MysqlCleaner

class OracleJournalMigratorTest
    extends JournalMigratorTest("oracle-application.conf")
    with JournalMigratorSpec.OracleCleaner

class SqlServerJournalMigratorTest
    extends JournalMigratorTest("sqlserver-application.conf")
    with JournalMigratorSpec.SqlServerCleaner
