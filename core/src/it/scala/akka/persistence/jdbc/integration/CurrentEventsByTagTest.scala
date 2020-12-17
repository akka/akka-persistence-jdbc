package akka.persistence.jdbc.integration

import akka.persistence.jdbc.query.{CurrentEventsByTagTest, MysqlCleaner, OracleCleaner, PostgresCleaner, SqlServerCleaner}

// Note: these tests use the shared-db configs, the test for all (so not only current) events use the regular db config

class PostgresScalaCurrentEventsByTagTest
  extends CurrentEventsByTagTest("postgres-shared-db-application.conf")
    with PostgresCleaner

class MySQLScalaCurrentEventsByTagTest
  extends CurrentEventsByTagTest("mysql-shared-db-application.conf")
    with MysqlCleaner

class OracleScalaCurrentEventsByTagTest
  extends CurrentEventsByTagTest("oracle-shared-db-application.conf")
    with OracleCleaner

class SqlServerScalaCurrentEventsByTagTest
  extends CurrentEventsByTagTest("sqlserver-shared-db-application.conf")
    with SqlServerCleaner
