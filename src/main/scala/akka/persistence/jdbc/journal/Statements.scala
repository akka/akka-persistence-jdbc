/*
 * Copyright 2015 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.jdbc.journal

import akka.persistence.jdbc.common.PluginConfig
import akka.persistence.jdbc.journal.RowTypeMarkers._
import akka.persistence.jdbc.serialization.{ JournalSerializer, JournalTypeConverter }
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.serialization.Serialization
import scalikejdbc._

import scala.collection.immutable.Seq
import scala.util.{ Failure, Success, Try }

trait JdbcStatements {

  def writeMessages(messages: Seq[AtomicWrite]): Seq[Try[Unit]]

  def writeMessage(message: AtomicWrite): Try[Unit]

  def deleteMessagesTo(persistenceId: String, toSequenceNr: Long)

  def readHighestSequenceNr(persistenceId: String): Long

  def replayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr]
}

trait GenericStatements extends JdbcStatements with JournalSerializer {
  implicit def session: DBSession

  def cfg: PluginConfig

  implicit def journalConverter: JournalTypeConverter

  implicit def serialization: Serialization

  val schema = cfg.journalSchemaName

  val table = cfg.journalTableName

  def writePersistentRepr(message: PersistentRepr): Try[Unit] = {
    import message._
    Try(SQL(s"INSERT INTO $schema$table (persistence_id, sequence_number, marker, message, created) VALUES (?,?,?,?, current_timestamp)")
      .bind(persistenceId, sequenceNr, AcceptedMarker, marshal(message)).update().apply)
  }

  override def writeMessage(message: AtomicWrite): Try[Unit] =
    message.payload.sortBy(_.sequenceNr).map(writePersistentRepr).find(_.isFailure).getOrElse(Success(Unit))

  def mkValuesStr(count: Long): String = "(?,?,?,?, current_timestamp)"

  def mkValuesInsertString(count: Long): String = (1L to count).map(mkValuesStr).mkString(",")

  case class MarshalledRepr(persistenceId: String, sequenceNr: Long, message: String)

  def marshalPersistentRepresentation(repr: PersistentRepr): MarshalledRepr =
    MarshalledRepr(repr.persistenceId, repr.sequenceNr, marshal(repr))

  def marshalAtomicWrite(write: AtomicWrite): Try[Seq[MarshalledRepr]] =
    Try(write.payload.map(marshalPersistentRepresentation))

  override def writeMessages(messages: Seq[AtomicWrite]): Seq[Try[Unit]] = {
    // every AtomicWrite contains a Seq[PersistentRepr], we have a sequence of AtomicWrite
    // and one AtomicWrite must all fail or all succeed
    // xsMarshalled is a converted sequence of AtomicWrite, that denotes whether an AtomicWrite
    // should be persisted (Try = Success) or not (Failed).
    val xsMarshalled: Seq[Try[Seq[MarshalledRepr]]] = messages.map(marshalAtomicWrite)
    val fields = xsMarshalled
      .collect { case Success(xs) ⇒ xs }
      .flatMap {
        _.map { case MarshalledRepr(persistenceId, sequenceNr, message) ⇒ List(persistenceId, sequenceNr, AcceptedMarker, message) }
      }
    val sql = s"INSERT INTO $schema$table (persistence_id, sequence_number, marker, message, created) VALUES " + mkValuesInsertString(fields.size)
    SQL(sql).bind(fields.flatten: _*).update().apply
    xsMarshalled.map(_.map(_ ⇒ ()))
  }

  override def deleteMessagesTo(persistenceId: String, toSequenceNr: Long) {
    SQL(s"DELETE FROM $schema$table WHERE sequence_number <= ? and persistence_id = ?")
      .bind(toSequenceNr, persistenceId).update().apply
  }

  override def readHighestSequenceNr(persistenceId: String): Long =
    SQL(s"SELECT MAX(sequence_number) FROM $schema$table WHERE persistence_id = ?")
      .bind(persistenceId)
      .map(_.longOpt(1))
      .single()
      .apply()
      .flatMap(identity)
      .getOrElse(0)

  override def replayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    SQL(s"SELECT message FROM $schema$table WHERE persistence_id = ? and (sequence_number >= ? and sequence_number <= ?) ORDER BY sequence_number LIMIT ?")
      .bind(persistenceId, fromSequenceNr, toSequenceNr, max)
      .map(rs ⇒ unmarshal(rs.string(1), persistenceId))
      .list()
      .apply
  }
}

trait PostgresqlStatements extends GenericStatements

trait MySqlStatements extends GenericStatements

trait H2Statements extends GenericStatements {
  override def replayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    val maxRecords = if (max == java.lang.Long.MAX_VALUE) java.lang.Integer.MAX_VALUE.toLong else max
    SQL(s"SELECT message FROM $schema$table WHERE persistence_id = ? and (sequence_number >= ? and sequence_number <= ?) ORDER BY sequence_number limit ?")
      .bind(persistenceId, fromSequenceNr, toSequenceNr, maxRecords)
      .map(rs ⇒ unmarshal(rs.string(1), persistenceId))
      .list()
      .apply
  }
}

trait OracleStatements extends GenericStatements {
  override def replayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    SQL(s"SELECT message FROM $schema$table WHERE persistence_id = ? AND (sequence_number >= ? AND sequence_number <= ?) AND ROWNUM <= ? ORDER BY sequence_number")
      .bind(persistenceId, fromSequenceNr, toSequenceNr, max)
      .map(rs ⇒ unmarshal(rs.string(1), persistenceId))
      .list()
      .apply
  }

  override def writeMessages(messages: Seq[AtomicWrite]): Seq[Try[Unit]] =
    messages.map(writeMessage)
}

trait MSSqlServerStatements extends GenericStatements {
  override def replayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    SQL(s"SELECT TOP(?) message FROM $schema$table WHERE persistence_id = ? AND (sequence_number >= ? AND sequence_number <= ?) ORDER BY sequence_number")
      .bind(max, persistenceId, fromSequenceNr, toSequenceNr)
      .map(rs ⇒ unmarshal(rs.string(1), persistenceId))
      .list()
      .apply
  }
}

trait DB2Statements extends GenericStatements {
  override def replayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    SQL(s"SELECT message FROM $schema$table WHERE persistence_id = ? AND (sequence_number >= ? AND sequence_number <= ?) ORDER BY sequence_number FETCH FIRST ? ROWS ONLY")
      .bind(persistenceId, fromSequenceNr, toSequenceNr, max)
      .map(rs ⇒ unmarshal(rs.string(1), persistenceId))
      .list()
      .apply
  }
}

trait InformixStatements extends GenericStatements
