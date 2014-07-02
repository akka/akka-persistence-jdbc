package akka.persistence.jdbc.journal

import akka.persistence.jdbc.common.ScalikeConnection
import akka.persistence.{Persistent, PersistentRepr}
import akka.persistence.jdbc.util.{EncodeDecode, Base64}
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future}

trait JdbcStatements {
  def selectMessage(persistenceId: String, sequenceNr: Long): Option[PersistentRepr]

  def insertMessage(persistenceId: String, sequenceNr: Long, marker: String = "A", message: Persistent)

  def updateMessage(persistenceId: String, sequenceNr: Long, marker: String, message: Persistent)

  def deleteMessageSingle(persistenceId: String, sequenceNr: Long)

  def deleteMessageRange(persistenceId: String, toSequenceNr: Long)

  def selectMaxSequenceNr(persistenceId: String): Future[Long]

  def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr]
}
trait GenericStatements extends JdbcStatements with ScalikeConnection with EncodeDecode {
  implicit def executionContext: ExecutionContext

  def selectMessage(persistenceId: String, sequenceNr: Long): Option[PersistentRepr] =
    sql"SELECT message FROM journal WHERE persistence_id = ${persistenceId} AND sequence_number = ${sequenceNr}"
      .map(rs => Journal.fromBytes(Base64.decodeBinary(rs.string(1))))
      .single()
      .apply()

  def insertMessage(persistenceId: String, sequenceNr: Long, marker: String = "A", message: Persistent) {
    val msgToWrite = Base64.encodeString(Journal.toBytes(message))
    sql"""INSERT INTO journal (persistence_id, sequence_number, marker, message, created) VALUES
            (${persistenceId},
            ${sequenceNr},
            ${marker},
            ${msgToWrite},
            current_timestamp)""".update.apply
  }

  def updateMessage(persistenceId: String, sequenceNr: Long, marker: String, message: Persistent) {
    val msgToWrite = Base64.encodeString(Journal.toBytes(message))
    sql"UPDATE journal SET message = ${msgToWrite}, marker = ${marker} WHERE persistence_id = ${persistenceId} and sequence_number = ${sequenceNr}".update.apply
  }

  def deleteMessageSingle(persistenceId: String, sequenceNr: Long) {
    sql"DELETE FROM journal WHERE sequence_number = ${sequenceNr} and persistence_id = ${persistenceId}".update.apply
  }

  def deleteMessageRange(persistenceId: String, toSequenceNr: Long) {
    sql"DELETE FROM journal WHERE sequence_number <= ${toSequenceNr} and persistence_id = ${persistenceId}".update.apply
  }

  def selectMaxSequenceNr(persistenceId: String): Future[Long] = Future[Long] {
    sql"SELECT MAX(sequence_number) FROM journal WHERE persistence_id = ${persistenceId}"
      .map(_.longOpt(1))
      .single()
      .apply()
      .flatMap(identity)
      .getOrElse(0)
  }

  def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    sql"SELECT message FROM journal WHERE persistence_id = ${persistenceId} and (sequence_number >= ${fromSequenceNr} and sequence_number <= ${toSequenceNr}) ORDER BY sequence_number limit ${max}"
      .map(rs => Journal.fromBytes(Base64.decodeBinary(rs.string(1))))
      .list()
      .apply
  }
}

trait PostgresqlStatements extends GenericStatements

trait MySqlStatements extends GenericStatements

trait H2Statements extends GenericStatements {
  override def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    val maxRecords = if(max == java.lang.Long.MAX_VALUE) java.lang.Integer.MAX_VALUE.toLong else max
    sql"SELECT message FROM journal WHERE persistence_id = ${persistenceId} and (sequence_number >= ${fromSequenceNr} and sequence_number <= ${toSequenceNr}) ORDER BY sequence_number limit ${maxRecords}"
      .map(rs => Journal.fromBytes(Base64.decodeBinary(rs.string(1))))
      .list()
      .apply
  }
}

trait OracleStatements extends GenericStatements {
  override def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    sql"SELECT message FROM journal WHERE persistence_id = ${persistenceId} AND (sequence_number >= ${fromSequenceNr} AND sequence_number <= ${toSequenceNr}) AND ROWNUM <= ${max} ORDER BY sequence_number"
      .map(rs => Journal.fromBytes(Base64.decodeBinary(rs.string(1))))
      .list()
      .apply
  }
}

trait MSSqlServerStatements extends GenericStatements {
  override def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    sql"SELECT TOP ${max} message FROM journal WHERE persistence_id = ${persistenceId} AND (sequence_number >= ${fromSequenceNr} AND sequence_number <= ${toSequenceNr}) ORDER BY sequence_number"
      .map(rs => Journal.fromBytes(Base64.decodeBinary(rs.string(1))))
      .list()
      .apply
  }
}

trait DB2Statements extends GenericStatements {
  override def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    sql"SELECT message FROM journal WHERE persistence_id = ${persistenceId} AND (sequence_number >= ${fromSequenceNr} AND sequence_number <= ${toSequenceNr}) ORDER BY sequence_number FETCH FIRST ${max} ROWS ONLY"
      .map(rs => Journal.fromBytes(Base64.decodeBinary(rs.string(1))))
      .list()
      .apply
  }
}