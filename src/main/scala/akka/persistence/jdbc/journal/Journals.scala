package akka.persistence.jdbc.journal

import akka.persistence.jdbc.extension.ScalikeExtension
import akka.serialization.SerializationExtension
import scalikejdbc.DBSession

import scala.concurrent.ExecutionContext

trait GenericJdbcSyncWriteJournal extends JdbcSyncWriteJournal with GenericStatements {
  implicit val session: DBSession = ScalikeExtension(context.system).session

  implicit val executionContext: ExecutionContext = context.system.dispatcher

  implicit val journalConverter = ScalikeExtension(context.system).journalConverter

  implicit val serialization = SerializationExtension(context.system)
}

class PostgresqlSyncWriteJournal extends GenericJdbcSyncWriteJournal with PostgresqlStatements

class MysqlSyncWriteJournal extends GenericJdbcSyncWriteJournal with MySqlStatements

class H2SyncWriteJournal extends GenericJdbcSyncWriteJournal with H2Statements

class OracleSyncWriteJournal extends GenericJdbcSyncWriteJournal with OracleStatements

class MSSqlServerSyncWriteJournal extends GenericJdbcSyncWriteJournal with MSSqlServerStatements

class InformixSyncWriteJournal extends GenericJdbcSyncWriteJournal with InformixStatements