package akka.persistence.jdbc.integration

import akka.persistence.jdbc.query.{
  JournalSequenceActorTest,
  MysqlCleaner,
  OracleCleaner,
  PostgresCleaner,
  SqlServerCleaner
}

class PostgresJournalSequenceActorTest
    extends JournalSequenceActorTest("postgres-application.conf", isOracle = false)
    with PostgresCleaner

class MySQLJournalSequenceActorTest
    extends JournalSequenceActorTest("mysql-application.conf", isOracle = false)
    with MysqlCleaner

class OracleJournalSequenceActorTest
    extends JournalSequenceActorTest("oracle-application.conf", isOracle = true)
    with OracleCleaner

class SqlServerJournalSequenceActorTest
    extends JournalSequenceActorTest("sqlserver-application.conf", isOracle = false)
    with SqlServerCleaner
