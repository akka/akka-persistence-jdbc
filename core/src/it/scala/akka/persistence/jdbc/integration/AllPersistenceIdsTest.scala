package akka.persistence.jdbc.integration

import akka.persistence.jdbc.query.{AllPersistenceIdsTest, MysqlCleaner, OracleCleaner, PostgresCleaner, SqlServerCleaner}

class PostgresScalaAllPersistenceIdsTest extends AllPersistenceIdsTest("postgres-application.conf") with PostgresCleaner

class MySQLScalaAllPersistenceIdsTest extends AllPersistenceIdsTest("mysql-application.conf") with MysqlCleaner

class OracleScalaAllPersistenceIdsTest extends AllPersistenceIdsTest("oracle-application.conf") with OracleCleaner

class SqlServerScalaAllPersistenceIdsTest
  extends AllPersistenceIdsTest("sqlserver-application.conf")
    with SqlServerCleaner
