package akka.persistence.jdbc.integration

import akka.persistence.jdbc.query.{
  HardDeleteQueryTest,
  MysqlCleaner,
  OracleCleaner,
  PostgresCleaner,
  SqlServerCleaner
}

class PostgresHardDeleteQueryTest
  extends HardDeleteQueryTest("postgres-application.conf")
    with PostgresCleaner

class MySQLHardDeleteQueryTest extends HardDeleteQueryTest("mysql-application.conf") with MysqlCleaner

class OracleHardDeleteQueryTest
  extends HardDeleteQueryTest("oracle-application.conf")
    with OracleCleaner

class SqlServerHardDeleteQueryTest
  extends HardDeleteQueryTest("sqlserver-application.conf")
    with SqlServerCleaner
