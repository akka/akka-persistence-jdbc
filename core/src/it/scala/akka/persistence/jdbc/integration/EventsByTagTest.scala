package akka.persistence.jdbc.integration

import akka.persistence.jdbc.query.{EventsByTagTest, MysqlCleaner, OracleCleaner, PostgresCleaner, SqlServerCleaner}

class PostgresScalaEventsByTagTest extends EventsByTagTest("postgres-application.conf") with PostgresCleaner

class MySQLScalaEventByTagTest extends EventsByTagTest("mysql-application.conf") with MysqlCleaner

class OracleScalaEventByTagTest extends EventsByTagTest("oracle-application.conf") with OracleCleaner {
  override def timeoutMultiplier: Int = 4
}

class SqlServerScalaEventByTagTest extends EventsByTagTest("sqlserver-application.conf") with SqlServerCleaner

