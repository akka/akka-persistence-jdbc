package akka.persistence.jdbc.integration

import akka.persistence.jdbc.query.{HardDeleteQueryTest, MysqlCleaner, OracleCleaner, PostgresCleaner, SqlServerCleaner}

class PostgresHardDeleteQueryTest
  extends HardDeleteQueryTest("postgres-application-with-hard-delete.conf")
    with PostgresCleaner

class MySQLHardDeleteQueryTest extends HardDeleteQueryTest("mysql-application-with-hard-delete.conf") with MysqlCleaner

class OracleHardDeleteQueryTest
  extends HardDeleteQueryTest("oracle-application-with-hard-delete.conf")
    with OracleCleaner

class SqlServerHardDeleteQueryTest
  extends HardDeleteQueryTest("sqlserver-application-with-hard-delete.conf")
    with SqlServerCleaner
