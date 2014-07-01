package akka.persistence.jdbc.journal

import akka.persistence.jdbc.common.ScalikeConnection
import akka.persistence.{Persistent, PersistentRepr}
import akka.persistence.jdbc.util.{EncodeDecode, Base64}
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future}

trait JdbcStatements {
  def selectMessage(processorId: String, sequenceNr: Long): Option[PersistentRepr]

  def insertMessage(processorId: String, sequenceNr: Long, marker: String = "A", message: Persistent)

  def updateMessage(processorId: String, sequenceNr: Long, marker: String, message: Persistent)

  def deleteMessageSingle(processorId: String, sequenceNr: Long)

  def deleteMessageRange(processorId: String, toSequenceNr: Long)

  def selectMaxSequenceNr(processorId: String): Future[Long]

  def selectMessagesFor(processorId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr]
}
trait GenericStatements extends JdbcStatements with ScalikeConnection with EncodeDecode {
  implicit def executionContext: ExecutionContext

  def selectMessage(processorId: String, sequenceNr: Long): Option[PersistentRepr] =
    sql"SELECT message FROM event_store WHERE processor_id = ${processorId} AND sequence_number = ${sequenceNr}"
      .map(rs => fromBytes(Base64.decodeBinary(rs.string(1))))
      .single()
      .apply()

  def insertMessage(processorId: String, sequenceNr: Long, marker: String = "A", message: Persistent) {
    val msgToWrite = Base64.encodeString(toBytes(message))
    sql"""INSERT INTO event_store (processor_id, sequence_number, marker, message, created) VALUES
            (${processorId},
            ${sequenceNr},
            ${marker},
            ${msgToWrite},
            current_timestamp)""".update.apply
  }

  def updateMessage(processorId: String, sequenceNr: Long, marker: String, message: Persistent) {
    val msgToWrite = Base64.encodeString(toBytes(message))
    sql"UPDATE event_store SET message = ${msgToWrite}, marker = ${marker} WHERE processor_id = ${processorId} and sequence_number = ${sequenceNr}".update.apply
  }

  def deleteMessageSingle(processorId: String, sequenceNr: Long) {
    sql"DELETE FROM event_store WHERE sequence_number = ${sequenceNr} and processor_id = ${processorId}".update.apply
  }

  def deleteMessageRange(processorId: String, toSequenceNr: Long) {
    sql"DELETE FROM event_store WHERE sequence_number <= ${toSequenceNr} and processor_id = ${processorId}".update.apply
  }

  def selectMaxSequenceNr(processorId: String): Future[Long] = Future[Long] {
    sql"SELECT MAX(sequence_number) FROM event_store WHERE processor_id = ${processorId}"
      .map(_.longOpt(1))
      .single()
      .apply()
      .flatMap(identity)
      .getOrElse(0)
  }

  def selectMessagesFor(processorId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    sql"SELECT message FROM event_store WHERE processor_id = ${processorId} and (sequence_number >= ${fromSequenceNr} and sequence_number <= ${toSequenceNr}) ORDER BY sequence_number limit ${max}"
      .map(rs => fromBytes(Base64.decodeBinary(rs.string(1))))
      .list()
      .apply
  }
}

trait PostgresqlStatements extends GenericStatements

trait MySqlStatements extends GenericStatements

trait H2Statements extends GenericStatements

trait OracleStatements extends GenericStatements {
  override def selectMessagesFor(processorId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    sql"SELECT message FROM event_store WHERE processor_id = ${processorId} AND (sequence_number >= ${fromSequenceNr} AND sequence_number <= ${toSequenceNr}) AND ROWNUM <= ${max} ORDER BY sequence_number"
      .map(rs => fromBytes(Base64.decodeBinary(rs.string(1))))
      .list()
      .apply
  }
}

trait MSSqlServerStatements extends GenericStatements {
  override def selectMessagesFor(processorId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    sql"SELECT TOP ${max} message FROM event_store WHERE processor_id = ${processorId} AND (sequence_number >= ${fromSequenceNr} AND sequence_number <= ${toSequenceNr}) ORDER BY sequence_number"
      .map(rs => fromBytes(Base64.decodeBinary(rs.string(1))))
      .list()
      .apply
  }
}

trait DB2Statements extends GenericStatements {
  override def selectMessagesFor(processorId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    sql"SELECT message FROM event_store WHERE processor_id = ${processorId} AND (sequence_number >= ${fromSequenceNr} AND sequence_number <= ${toSequenceNr}) ORDER BY sequence_number FETCH FIRST ${max} ROWS ONLY"
      .map(rs => fromBytes(Base64.decodeBinary(rs.string(1))))
      .list()
      .apply
  }
}