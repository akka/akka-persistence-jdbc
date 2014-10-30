package akka.persistence.jdbc.journal

import akka.persistence.PersistentRepr
import akka.persistence.jdbc.common.PluginConfig
import akka.persistence.jdbc.util.{ByteString, Base64, EncodeDecode}
import scalikejdbc._

import scala.concurrent.{ExecutionContext, Future}

trait JdbcStatements {
  def selectMessage(persistenceId: String, sequenceNr: Long): Option[PersistentRepr]

  def insertMessage(persistenceId: String, sequenceNr: Long, marker: String = "A", message: PersistentRepr)

  def updateMessage(persistenceId: String, sequenceNr: Long, marker: String, message: PersistentRepr)

  def deleteMessageSingle(persistenceId: String, sequenceNr: Long)

  def deleteMessageRange(persistenceId: String, toSequenceNr: Long)

  def selectMaxSequenceNr(persistenceId: String): Future[Long]

  def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr]
}

trait GenericStatements extends JdbcStatements with EncodeDecode {
  implicit val executionContext: ExecutionContext
  implicit val session: DBSession
  val cfg: PluginConfig

  implicit val base64 = cfg.base64
  val schema = cfg.journalSchemaName
  val table = cfg.journalTableName

  def selectMessage(persistenceId: String, sequenceNr: Long): Option[PersistentRepr] =
    SQL(s"SELECT message FROM $schema$table WHERE persistence_id = ? AND sequence_number = ?").bind(persistenceId, sequenceNr)
      .map(rs => Journal.fromBytes(decodeBinary(rs.string(1))))
      .single()
      .apply()

  def insertMessage(persistenceId: String, sequenceNr: Long, marker: String = "A", message: PersistentRepr) {
    val msgToWrite = encodeString(Journal.toBytes(message))
    SQL(s"INSERT INTO $schema$table (persistence_id, sequence_number, marker, message, created) VALUES (?,?,?,?, current_timestamp)")
      .bind(persistenceId, sequenceNr, marker, msgToWrite).update().apply
  }

  def updateMessage(persistenceId: String, sequenceNr: Long, marker: String, message: PersistentRepr) {
    val msgToWrite = encodeString(Journal.toBytes(message))
    SQL(s"UPDATE $schema$table SET message = ?, marker = ? WHERE persistence_id = ? and sequence_number = ?")
      .bind(msgToWrite, marker, persistenceId, sequenceNr).update().apply
  }

  def deleteMessageSingle(persistenceId: String, sequenceNr: Long) {
    SQL(s"DELETE FROM $schema$table WHERE sequence_number = ? and persistence_id = ?")
      .bind(sequenceNr, persistenceId).update().apply
  }

  def deleteMessageRange(persistenceId: String, toSequenceNr: Long) {
    SQL(s"DELETE FROM $schema$table WHERE sequence_number <= ? and persistence_id = ?")
      .bind(toSequenceNr, persistenceId).update().apply
  }

  def selectMaxSequenceNr(persistenceId: String): Future[Long] = Future[Long] {
    SQL(s"SELECT MAX(sequence_number) FROM $schema$table WHERE persistence_id = ?")
      .bind(persistenceId)
      .map(_.longOpt(1))
      .single()
      .apply()
      .flatMap(identity)
      .getOrElse(0)
  }

  def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    SQL(s"SELECT message FROM $schema$table WHERE persistence_id = ? and (sequence_number >= ? and sequence_number <= ?) ORDER BY sequence_number LIMIT ?")
      .bind(persistenceId, fromSequenceNr, toSequenceNr, max)
      .map(rs => Journal.fromBytes(decodeBinary(rs.string(1))))
      .list()
      .apply
  }
}

trait PostgresqlStatements extends GenericStatements

trait MySqlStatements extends GenericStatements

trait H2Statements extends GenericStatements {
  override def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    val maxRecords = if (max == java.lang.Long.MAX_VALUE) java.lang.Integer.MAX_VALUE.toLong else max
    SQL(s"SELECT message FROM $schema$table WHERE persistence_id = ? and (sequence_number >= ? and sequence_number <= ?) ORDER BY sequence_number limit ?")
      .bind(persistenceId, fromSequenceNr, toSequenceNr, maxRecords)
      .map(rs => Journal.fromBytes(decodeBinary(rs.string(1))))
      .list()
      .apply
  }
}

trait OracleStatements extends GenericStatements {
  override def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    SQL(s"SELECT message FROM $schema$table WHERE persistence_id = ? AND (sequence_number >= ? AND sequence_number <= ?) AND ROWNUM <= ? ORDER BY sequence_number")
      .bind(persistenceId, fromSequenceNr, toSequenceNr, max)
      .map(rs => Journal.fromBytes(decodeBinary(rs.string(1))))
      .list()
      .apply
  }
}

trait MSSqlServerStatements extends GenericStatements {
  override def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    SQL(s"SELECT TOP ? message FROM $schema$table WHERE persistence_id = ? AND (sequence_number >= ${} AND sequence_number <= ?) ORDER BY sequence_number")
      .bind(max, persistenceId, fromSequenceNr, toSequenceNr)
      .map(rs => Journal.fromBytes(decodeBinary(rs.string(1))))
      .list()
      .apply
  }
}

trait DB2Statements extends GenericStatements {
  override def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    SQL(s"SELECT message FROM $schema$table WHERE persistence_id = ? AND (sequence_number >= ? AND sequence_number <= ?) ORDER BY sequence_number FETCH FIRST ? ROWS ONLY")
      .bind(persistenceId, fromSequenceNr, toSequenceNr, max)
      .map(rs => Journal.fromBytes(decodeBinary(rs.string(1))))
      .list()
      .apply
  }
}

trait InformixStatements extends GenericStatements