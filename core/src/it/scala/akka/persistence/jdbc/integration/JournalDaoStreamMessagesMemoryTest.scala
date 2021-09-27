package akka.persistence.jdbc.integration

import akka.persistence.jdbc.query.{
  JournalDaoStreamMessagesMemoryTest,
  MysqlCleaner,
  OracleCleaner,
  PostgresCleaner,
  SqlServerCleaner
}

class PostgresJournalDaoStreamMessagesMemoryTest
    extends JournalDaoStreamMessagesMemoryTest("postgres-application.conf")
    with PostgresCleaner

class MySQLJournalDaoStreamMessagesMemoryTest
    extends JournalDaoStreamMessagesMemoryTest("mysql-application.conf")
    with MysqlCleaner

class OracleJournalDaoStreamMessagesMemoryTest
    extends JournalDaoStreamMessagesMemoryTest("oracle-application.conf")
    with OracleCleaner

class SqlServerJournalDaoStreamMessagesMemoryTest
    extends JournalDaoStreamMessagesMemoryTest("sqlserver-application.conf")
    with SqlServerCleaner
