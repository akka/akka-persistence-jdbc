package akka.persistence.jdbc.integration

import akka.persistence.jdbc.query.{
  EventsByPersistenceIdTest,
  MysqlCleaner,
  OracleCleaner,
  PostgresCleaner,
  SqlServerCleaner
}

class PostgresScalaEventsByPersistenceIdTest
    extends EventsByPersistenceIdTest("postgres-application.conf")
    with PostgresCleaner

class MySQLScalaEventsByPersistenceIdTest extends EventsByPersistenceIdTest("mysql-application.conf") with MysqlCleaner

class OracleScalaEventsByPersistenceIdTest
    extends EventsByPersistenceIdTest("oracle-application.conf")
    with OracleCleaner

class SqlServerScalaEventsByPersistenceIdTest
    extends EventsByPersistenceIdTest("sqlserver-application.conf")
    with SqlServerCleaner
