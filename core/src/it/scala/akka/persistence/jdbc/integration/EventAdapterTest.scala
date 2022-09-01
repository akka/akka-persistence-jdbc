package akka.persistence.jdbc.integration

import akka.persistence.jdbc.query.{ EventAdapterTest, MysqlCleaner, OracleCleaner, PostgresCleaner, SqlServerCleaner }

class PostgresScalaEventAdapterTest extends EventAdapterTest("postgres-application.conf") with PostgresCleaner

class MySQLScalaEventAdapterTest extends EventAdapterTest("mysql-application.conf") with MysqlCleaner

class OracleScalaEventAdapterTest extends EventAdapterTest("oracle-application.conf") with OracleCleaner //test

class SqlServerScalaEventAdapterTest extends EventAdapterTest("sqlserver-application.conf") with SqlServerCleaner
