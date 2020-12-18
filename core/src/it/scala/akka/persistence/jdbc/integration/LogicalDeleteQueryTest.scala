package akka.persistence.jdbc.integration

import akka.persistence.jdbc.query.{LogicalDeleteQueryTest, MysqlCleaner, OracleCleaner, PostgresCleaner, SqlServerCleaner}

class PostgresLogicalDeleteQueryTest extends LogicalDeleteQueryTest("postgres-application.conf") with PostgresCleaner

class MySQLLogicalDeleteQueryTest extends LogicalDeleteQueryTest("mysql-application.conf") with MysqlCleaner

class OracleLogicalDeleteQueryTest extends LogicalDeleteQueryTest("oracle-application.conf") with OracleCleaner

class SqlServerLogicalDeleteQueryTest extends LogicalDeleteQueryTest("sqlserver-application.conf") with SqlServerCleaner
