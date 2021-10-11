package akka.persistence.jdbc.integration

import akka.persistence.jdbc.query.{
  CurrentPersistenceIdsTest,
  MysqlCleaner,
  OracleCleaner,
  PostgresCleaner,
  SqlServerCleaner
}

// Note: these tests use the shared-db configs, the test for all persistence ids use the regular db config
class PostgresScalaCurrentPersistenceIdsTest
    extends CurrentPersistenceIdsTest("postgres-shared-db-application.conf")
    with PostgresCleaner

class MySQLScalaCurrentPersistenceIdsTest
    extends CurrentPersistenceIdsTest("mysql-shared-db-application.conf")
    with MysqlCleaner

class OracleScalaCurrentPersistenceIdsTest
    extends CurrentPersistenceIdsTest("oracle-shared-db-application.conf")
    with OracleCleaner

class SqlServerScalaCurrentPersistenceIdsTest
    extends CurrentPersistenceIdsTest("sqlserver-application.conf")
    with SqlServerCleaner
