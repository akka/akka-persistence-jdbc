package akka.persistence.jdbc.integration

import akka.persistence.jdbc.query.{CurrentEventsByPersistenceIdTest, MysqlCleaner, OracleCleaner, PostgresCleaner, SqlServerCleaner}

// Note: these tests use the shared-db configs, the test for all (so not only current) events use the regular db config

class PostgresScalaCurrentEventsByPersistenceIdTest
  extends CurrentEventsByPersistenceIdTest("postgres-shared-db-application.conf")
    with PostgresCleaner

class MySQLScalaCurrentEventsByPersistenceIdTest
  extends CurrentEventsByPersistenceIdTest("mysql-shared-db-application.conf")
    with MysqlCleaner

class OracleScalaCurrentEventsByPersistenceIdTest
  extends CurrentEventsByPersistenceIdTest("oracle-shared-db-application.conf")
    with OracleCleaner

class SqlServerScalaCurrentEventsByPersistenceIdTest
  extends CurrentEventsByPersistenceIdTest("sqlserver-shared-db-application.conf")
    with SqlServerCleaner
