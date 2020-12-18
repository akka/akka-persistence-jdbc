package akka.persistence.jdbc.integration

import akka.persistence.jdbc.journal.JdbcJournalSpec
import akka.persistence.jdbc.testkit.internal.MySQL
import akka.persistence.jdbc.testkit.internal.Oracle
import akka.persistence.jdbc.testkit.internal.Postgres
import akka.persistence.jdbc.testkit.internal.SqlServer
import com.typesafe.config.{ ConfigFactory, ConfigValueFactory }

class PostgresJournalSpec extends JdbcJournalSpec(ConfigFactory.load("postgres-application.conf"), Postgres)
class PostgresJournalSpecSharedDb
    extends JdbcJournalSpec(ConfigFactory.load("postgres-shared-db-application.conf"), Postgres)
class PostgresJournalSpecPhysicalDelete
    extends JdbcJournalSpec(
      ConfigFactory
        .load("postgres-application.conf")
        .withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false)),
      Postgres)

class MySQLJournalSpec extends JdbcJournalSpec(ConfigFactory.load("mysql-application.conf"), MySQL)
class MySQLJournalSpecSharedDb extends JdbcJournalSpec(ConfigFactory.load("mysql-shared-db-application.conf"), MySQL)
class MySQLJournalSpecPhysicalDelete
    extends JdbcJournalSpec(
      ConfigFactory
        .load("mysql-application.conf")
        .withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false)),
      MySQL)

class OracleJournalSpec extends JdbcJournalSpec(ConfigFactory.load("oracle-application.conf"), Oracle)
class OracleJournalSpecSharedDb extends JdbcJournalSpec(ConfigFactory.load("oracle-shared-db-application.conf"), Oracle)
class OracleJournalSpecPhysicalDelete
    extends JdbcJournalSpec(
      ConfigFactory
        .load("oracle-application.conf")
        .withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false)),
      Oracle)

class SqlServerJournalSpec extends JdbcJournalSpec(ConfigFactory.load("sqlserver-application.conf"), SqlServer)
class SqlServerJournalSpecSharedDb
    extends JdbcJournalSpec(ConfigFactory.load("sqlserver-shared-db-application.conf"), SqlServer)
class SqlServerJournalSpecPhysicalDelete
    extends JdbcJournalSpec(
      ConfigFactory
        .load("sqlserver-application.conf")
        .withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false)),
      SqlServer)
