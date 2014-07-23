package akka.persistence.jdbc.journal

import java.io.ByteArrayInputStream
import java.sql.ResultSet

import akka.persistence.jdbc.common.ScalikeConnection
import akka.persistence.{PersistentActor, Persistent, PersistentRepr}
import akka.persistence.jdbc.util.{EncodeDecode, Base64}
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

trait GenericStatements extends JdbcStatements with ScalikeConnection with EncodeDecode {
  implicit def executionContext: ExecutionContext

  def selectMessage(persistenceId: String, sequenceNr: Long): Option[PersistentRepr] =
    sql"SELECT message FROM journal WHERE persistence_id = ${persistenceId} AND sequence_number = ${sequenceNr}"
      .map(rs => Journal.fromBytes(Base64.decodeBinary(rs.string(1))))
      .single()
      .apply()

  def insertMessage(persistenceId: String, sequenceNr: Long, marker: String = "A", message: PersistentRepr) {
    val msgToWrite = Base64.encodeString(Journal.toBytes(message))
    sql"""INSERT INTO journal (persistence_id, sequence_number, marker, message, created) VALUES
            (${persistenceId},
            ${sequenceNr},
            ${marker},
            ${msgToWrite},
            current_timestamp)""".update.apply
  }

  def updateMessage(persistenceId: String, sequenceNr: Long, marker: String, message: PersistentRepr) {
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
    sql"SELECT message FROM journal WHERE persistence_id = ${persistenceId} and (sequence_number >= ${fromSequenceNr} and sequence_number <= ${toSequenceNr}) ORDER BY sequence_number LIMIT ${max}"
      .map(rs => Journal.fromBytes(Base64.decodeBinary(rs.string(1))))
      .list()
      .apply
  }
}

trait PostgresqlStatements extends GenericStatements

trait MySqlStatements extends GenericStatements

trait H2Statements extends GenericStatements {
  override def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    val maxRecords = if (max == java.lang.Long.MAX_VALUE) java.lang.Integer.MAX_VALUE.toLong else max
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

trait InformixStatements extends GenericStatements {
  override def selectMessage(persistenceId: String, sequenceNr: Long): Option[PersistentRepr] = {
    using(ConnectionPool.borrow()) { conn =>
      val str = s"SELECT message FROM journal WHERE persistence_id = '$persistenceId' AND sequence_number = $sequenceNr"
      val rs = conn.createStatement().executeQuery(str)
      if(rs.next()) {
        val is = rs.getAsciiStream(1)
        val base64Str = scala.io.Source.fromInputStream(is).mkString
        Some(Journal.fromBytes(Base64.decodeBinary(base64Str)))
      }
      else None
    }
  }

  override def insertMessage(persistenceId: String, sequenceNr: Long, marker: String = "A", message: PersistentRepr) {
    val arr = Base64.encodeString(Journal.toBytes(message))
    val msgToWrite = new ByteArrayInputStream(arr.getBytes)
    using(ConnectionPool.borrow()) { conn =>
      val stmt = conn.prepareStatement("INSERT INTO journal (persistence_id, sequence_number, marker, message, created) VALUES (?, ?, ?, ?, CURRENT YEAR TO FRACTION(5))")
      stmt.setString(1, persistenceId)
      stmt.setLong(2, sequenceNr)
      stmt.setString(3, marker)
      stmt.setAsciiStream(4, msgToWrite, arr.getBytes.length)
      stmt.executeUpdate()
      stmt.close()
    }
  }

  override def updateMessage(persistenceId: String, sequenceNr: Long, marker: String, message: PersistentRepr) {
    val arr = Base64.encodeString(Journal.toBytes(message))
    val msgToWrite = new ByteArrayInputStream(arr.getBytes)
    using(ConnectionPool.borrow()) { conn =>
      val stmt = conn.prepareStatement("UPDATE journal SET message = ?, marker = ? WHERE persistence_id = ? and sequence_number = ?")
      stmt.setAsciiStream(1, msgToWrite)
      stmt.setString(2, marker)
      stmt.setString(3, persistenceId)
      stmt.setLong(4, sequenceNr)
      stmt.executeUpdate()
      stmt.close()
    }
  }

  override def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    def toList(rs: ResultSet, list: List[PersistentRepr]): List[PersistentRepr] = {
      if (rs.next()) {
        val is = rs.getAsciiStream(1)
        val base64Str = scala.io.Source.fromInputStream(is).mkString
        toList(rs, Journal.fromBytes(Base64.decodeBinary(base64Str)) :: list)
      }
      else list.reverse
    }

    if (max == 0) Nil
    else {
      using(ConnectionPool.borrow()) { conn =>
        val str = s"SELECT message FROM journal WHERE persistence_id = '$persistenceId' and (sequence_number >= $fromSequenceNr and sequence_number <= $toSequenceNr) ORDER BY sequence_number LIMIT $max"
        val rs = conn.createStatement().executeQuery(str)
        toList(rs, Nil)
      }
    }
  }
}