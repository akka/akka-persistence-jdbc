package akka.persistence.jdbc.integration

import akka.persistence.jdbc.journal.JdbcJournalSpec
import akka.persistence.jdbc.testkit.internal.{ MySQL, Oracle, Postgres, SqlServer }
import com.typesafe.config.ConfigFactory

class PostgresJournalSpec extends JdbcJournalSpec(ConfigFactory.load("postgres-application.conf"), Postgres)
class PostgresJournalSpecSharedDb
    extends JdbcJournalSpec(ConfigFactory.load("postgres-shared-db-application.conf"), Postgres)

class MySQLJournalSpec extends JdbcJournalSpec(ConfigFactory.load("mysql-application.conf"), MySQL)
class MySQLJournalSpecSharedDb extends JdbcJournalSpec(ConfigFactory.load("mysql-shared-db-application.conf"), MySQL)

class OracleJournalSpec extends JdbcJournalSpec(ConfigFactory.load("oracle-application.conf"), Oracle)
class OracleJournalSpecSharedDb extends JdbcJournalSpec(ConfigFactory.load("oracle-shared-db-application.conf"), Oracle)

class SqlServerJournalSpec extends JdbcJournalSpec(ConfigFactory.load("sqlserver-application.conf"), SqlServer)
class SqlServerJournalSpecSharedDb
    extends JdbcJournalSpec(ConfigFactory.load("sqlserver-shared-db-application.conf"), SqlServer)
