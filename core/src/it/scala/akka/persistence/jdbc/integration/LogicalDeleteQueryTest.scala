package akka.persistence.jdbc.integration

import akka.persistence.jdbc.query.{
  LogicalDeleteQueryTest,
  MysqlCleaner,
  OracleCleaner,
  PostgresCleaner,
  SqlServerCleaner
}

class PostgresLogicalDeleteQueryTest extends LogicalDeleteQueryTest("postgres-application-with-logical-delete.conf") with PostgresCleaner

class MySQLLogicalDeleteQueryTest extends LogicalDeleteQueryTest("mysql-application-with-logical-delete.conf") with MysqlCleaner

class OracleLogicalDeleteQueryTest extends LogicalDeleteQueryTest("oracle-application-with-logical-delete.conf") with OracleCleaner

class SqlServerLogicalDeleteQueryTest extends LogicalDeleteQueryTest("sqlserver-application-with-logical-delete.conf") with SqlServerCleaner
