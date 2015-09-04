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
  /**
   * Synchronously writes a batch (`Seq`) of persistent messages to the
   * journal.
   *
   * The batch is only for performance reasons, i.e. all messages don't have to be written
   * atomically. Higher throughput can typically be achieved by using batch inserts of many
   * records compared to inserting records one-by-one, but this aspect depends on the
   * underlying data store and a journal implementation can implement it as efficient as
   * possible. Journals should aim to persist events in-order for a given `persistenceId`
   * as otherwise in case of a failure, the persistent state may be end up being inconsistent.
   *
   * Each `AtomicWrite` message contains the single `PersistentRepr` that corresponds to
   * the event that was passed to the `persist` method of the `PersistentActor`, or it
   * contains several `PersistentRepr` that corresponds to the events that were passed
   * to the `persistAll` method of the `PersistentActor`. All `PersistentRepr` of the
   * `AtomicWrite` must be written to the data store atomically, i.e. all or none must
   * be stored. If the journal (data store) cannot support atomic writes of multiple
   * events it should reject such writes with a `Try` `Failure` with an
   * `UnsupportedOperationException` describing the issue. This limitation should
   * also be documented by the journal plugin.
   *
   * If there are failures when storing any of the messages in the batch the returned
   * `Future` must be completed with failure. The `Future` must only be completed with
   * success when all messages in the batch have been confirmed to be stored successfully,
   * i.e. they will be readable, and visible, in a subsequent replay. If there is
   * uncertainty about if the messages were stored or not the `Future` must be completed
   * with failure.
   *
   * Data store connection problems must be signaled by completing the `Future` with
   * failure.
   *
   * The journal can also signal that it rejects individual messages (`AtomicWrite`) by
   * the returned `immutable.Seq[Try[Unit]]`. It is possible but not mandatory to reduce
   * number of allocations by returning `Future.successful(Nil)` for the happy path,
   * i.e. when no messages are rejected. Otherwise the returned `Seq` must have as many elements
   * as the input `messages` `Seq`. Each `Try` element signals if the corresponding
   * `AtomicWrite` is rejected or not, with an exception describing the problem. Rejecting
   * a message means it was not stored, i.e. it must not be included in a later replay.
   * Rejecting a message is typically done before attempting to store it, e.g. because of
   * serialization error.
   *
   * Data store connection problems must not be signaled as rejections.
   *
   * It is possible but not mandatory to reduce number of allocations by returning
   * `Future.successful(Nil)` for the happy path, i.e. when no messages are rejected.
   */
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

  case class InsertVO(persistenceId: String, sequenceNr: Long, message: String)
  
  def marshalPersistentRepresentation(repr: PersistentRepr): Try[(String, Long, String)] =
    Try(repr.persistenceId, repr.sequenceNr, marshal(repr))
  
  def marshalAtomicWrite(write: AtomicWrite): Seq[Try[(String, Long, String)]] = 
    write.payload.map(marshalPersistentRepresentation)

  override def writeMessages(messages: Seq[AtomicWrite]): Seq[Try[Unit]] = {
    // every AtomicWrite contains a Seq[PersistentRepr], we have a sequence of AtomicWrite
    // the response must be that the Seq[AtomicWrite] has failed
    val marshalled: Seq[Seq[Try[(String, Long, String)]]] = messages.map(marshalAtomicWrite)
    val sql = s"INSERT INTO $schema$table (persistence_id, sequence_number, marker, message, created) VALUES "



    messages.flatMap(_.payload).map { repr ⇒
      List(repr.persistenceId, repr.sequenceNr, AcceptedMarker, Try(marshal(repr)))
    }

    
    SQL(sql).bind(args: _*).update().apply
    Nil
  }.recover {
    case t: Throwable ⇒
      t.printStackTrace
      List(Failure(t))
  }.getOrElse(Nil)

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
