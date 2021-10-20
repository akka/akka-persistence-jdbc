package akka.persistence.jdbc.migrator.integration

import akka.persistence.jdbc.migrator.MigratorSpec._
import akka.persistence.jdbc.migrator.SnapshotMigratorTest

class PostgresSnapshotMigratorTest extends SnapshotMigratorTest("postgres-application.conf") with PostgresCleaner

class MySQLSnapshotMigratorTest extends SnapshotMigratorTest("mysql-application.conf") with MysqlCleaner

class OracleSnapshotMigratorTest extends SnapshotMigratorTest("oracle-application.conf") with OracleCleaner

class SqlServerSnapshotMigratorTest extends SnapshotMigratorTest("sqlserver-application.conf") with SqlServerCleaner
