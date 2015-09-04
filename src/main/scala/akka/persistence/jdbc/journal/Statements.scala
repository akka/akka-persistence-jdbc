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

import akka.persistence.PersistentRepr
import akka.persistence.jdbc.common.PluginConfig
import akka.persistence.jdbc.journal.RowTypeMarkers._
import akka.persistence.jdbc.serialization.{ JournalSerializer, JournalTypeConverter }
import akka.serialization.Serialization
import scalikejdbc._

import scala.collection.immutable.Seq

trait JdbcStatements {
  def selectMessage(persistenceId: String, sequenceNr: Long): Option[PersistentRepr]

  def insertMessage(message: PersistentRepr): Int

  def insertMessages(messages: Seq[PersistentRepr]): Int

  def updateMessage(persistenceId: String, sequenceNr: Long, marker: String, message: PersistentRepr): Int

  def deleteMessageSingle(persistenceId: String, sequenceNr: Long)

  def deleteMessageRange(persistenceId: String, toSequenceNr: Long)

  def selectMaxSequenceNr(persistenceId: String): Long

  def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr]
}

trait GenericStatements extends JdbcStatements with JournalSerializer {
  implicit def session: DBSession
  implicit def journalConverter: JournalTypeConverter
  implicit def serialization: Serialization

  def cfg: PluginConfig
  def schema = cfg.journalSchemaName
  def table = cfg.journalTableName

  def selectMessage(persistenceId: String, sequenceNr: Long): Option[PersistentRepr] =
    SQL(s"SELECT message FROM $schema$table WHERE persistence_id = ? AND sequence_number = ?").bind(persistenceId, sequenceNr)
      .map(rs ⇒ unmarshal(rs.string(1), persistenceId))
      .single()
      .apply()

  def insertMessage(message: PersistentRepr): Int = {
    import message._
    SQL(s"INSERT INTO $schema$table (persistence_id, sequence_number, marker, message, created) VALUES (?,?,?,?, current_timestamp)")
      .bind(processorId, sequenceNr, AcceptedMarker, marshal(message)).update().apply
  }

  override def insertMessages(messages: Seq[PersistentRepr]): Int = {
    val sql = s"INSERT INTO $schema$table (persistence_id, sequence_number, marker, message, created) VALUES " +
      messages.map { _ ⇒
        "(?,?,?,?, current_timestamp)"
      }.mkString(",")
    val args = messages.flatMap { repr ⇒
      List(repr.processorId, repr.sequenceNr, AcceptedMarker, marshal(repr))
    }
    SQL(sql).bind(args: _*).update().apply
  }

  def updateMessage(persistenceId: String, sequenceNr: Long, marker: String, message: PersistentRepr): Int =
    SQL(s"UPDATE $schema$table SET message = ?, marker = ? WHERE persistence_id = ? and sequence_number = ?")
      .bind(marshal(message), marker, persistenceId, sequenceNr).update().apply

  def deleteMessageSingle(persistenceId: String, sequenceNr: Long): Unit =
    SQL(s"DELETE FROM $schema$table WHERE sequence_number = ? and persistence_id = ?")
      .bind(sequenceNr, persistenceId).update().apply

  def deleteMessageRange(persistenceId: String, toSequenceNr: Long): Unit =
    SQL(s"DELETE FROM $schema$table WHERE sequence_number <= ? and persistence_id = ?")
      .bind(toSequenceNr, persistenceId).update().apply

  def selectMaxSequenceNr(persistenceId: String): Long =
    SQL(s"SELECT MAX(sequence_number) FROM $schema$table WHERE persistence_id = ?")
      .bind(persistenceId)
      .map(_.longOpt(1))
      .single()
      .apply()
      .flatMap(identity)
      .getOrElse(0)

  def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] =
    SQL(s"SELECT message FROM $schema$table WHERE persistence_id = ? and (sequence_number >= ? and sequence_number <= ?) ORDER BY sequence_number LIMIT ?")
      .bind(persistenceId, fromSequenceNr, toSequenceNr, max)
      .map(rs ⇒ unmarshal(rs.string(1), persistenceId))
      .list()
      .apply
}

trait PostgresqlStatements extends GenericStatements

trait MySqlStatements extends GenericStatements

trait H2Statements extends GenericStatements {
  override def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    val maxRecords = if (max == java.lang.Long.MAX_VALUE) java.lang.Integer.MAX_VALUE.toLong else max
    SQL(s"SELECT message FROM $schema$table WHERE persistence_id = ? and (sequence_number >= ? and sequence_number <= ?) ORDER BY sequence_number limit ?")
      .bind(persistenceId, fromSequenceNr, toSequenceNr, maxRecords)
      .map(rs ⇒ unmarshal(rs.string(1), persistenceId))
      .list()
      .apply
  }
}

trait OracleStatements extends GenericStatements {
  override def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    SQL(s"SELECT message FROM $schema$table WHERE persistence_id = ? AND (sequence_number >= ? AND sequence_number <= ?) AND ROWNUM <= ? ORDER BY sequence_number")
      .bind(persistenceId, fromSequenceNr, toSequenceNr, max)
      .map(rs ⇒ unmarshal(rs.string(1), persistenceId))
      .list()
      .apply
  }

  override def insertMessages(messages: Seq[PersistentRepr]): Int = messages.map(insertMessage).sum
}

trait MSSqlServerStatements extends GenericStatements {
  override def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    SQL(s"SELECT TOP(?) message FROM $schema$table WHERE persistence_id = ? AND (sequence_number >= ? AND sequence_number <= ?) ORDER BY sequence_number")
      .bind(max, persistenceId, fromSequenceNr, toSequenceNr)
      .map(rs ⇒ unmarshal(rs.string(1), persistenceId))
      .list()
      .apply
  }
}

trait DB2Statements extends GenericStatements {
  override def selectMessagesFor(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): List[PersistentRepr] = {
    SQL(s"SELECT message FROM $schema$table WHERE persistence_id = ? AND (sequence_number >= ? AND sequence_number <= ?) ORDER BY sequence_number FETCH FIRST ? ROWS ONLY")
      .bind(persistenceId, fromSequenceNr, toSequenceNr, max)
      .map(rs ⇒ unmarshal(rs.string(1), persistenceId))
      .list()
      .apply
  }
}

trait InformixStatements extends GenericStatements
