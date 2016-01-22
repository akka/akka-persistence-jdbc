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

package akka.persistence.jdbc.dao

import akka.persistence.jdbc.dao.JournalTables.{ JournalDeletedToRow, JournalRow }
import akka.persistence.jdbc.extension.{ DeletedToTableConfiguration, JournalTableConfiguration }
import akka.persistence.jdbc.serialization.Serialized
import akka.persistence.jdbc.util.SlickDriver
import akka.stream.scaladsl._
import akka.stream.{ FlowShape, Materializer }
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Success, Try }

object JournalDao {
  /**
   * Factory method
   */
  def apply(driver: String, db: JdbcBackend#Database, journalTableCfg: JournalTableConfiguration, deletedToTableCfg: DeletedToTableConfiguration)(implicit ec: ExecutionContext, mat: Materializer): JournalDao =
    if (SlickDriver.forDriverName.isDefinedAt(driver)) {
      new JdbcSlickJournalDao(db, SlickDriver.forDriverName(driver), journalTableCfg, deletedToTableCfg)
    } else throw new IllegalArgumentException("Unknown slick driver: " + driver)
}

trait JournalDao {

  /**
   * Writes serialized messages
   */
  def writeList(xs: Iterable[Serialized]): Future[Unit]

  /**
   * Writes serialized messages
   */
  def writeFlow: Flow[Try[Iterable[Serialized]], Try[Iterable[Serialized]], Unit]

  /**
   * Deletes all persistent messages up to toSequenceNr (inclusive) for the persistenceId
   */
  def delete(persistenceId: String, toSequenceNr: Long): Future[Unit]

  /**
   * Returns the highest sequence number for the events that are stored for that `persistenceId`. When no events are
   * found for the `persistenceId`, 0L will be the highest sequence number
   */
  def highestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long]

  /**
   * Returns a Source of bytes for a certain persistenceId
   */
  def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[Array[Byte], Unit]

  /**
   * Returns distinct stream of persistenceIds
   */
  def allPersistenceIdsSource: Source[String, Unit]

  /**
   * Returns the persistenceIds that are available on request of a query list of persistence ids
   */
  def persistenceIds(queryListOfPersistenceIds: Iterable[String]): Future[Seq[String]]

  //  /**
  //   * Returns a Source of bytes for certain tags from an offset. The result is sorted by
  //   * created time asc thus the offset is relative to the creation time
  //   */
  //  def eventsByTag(tag: String, offset: Long): Source[Array[Byte], Unit]
}

trait WriteMessagesFacade {
  def writeMessages: Flow[Try[Iterable[Serialized]], Try[Iterable[Serialized]], Unit]
}

class FlowGraphWriteMessagesFacade(journalDao: JournalDao)(implicit ec: ExecutionContext, mat: Materializer) extends WriteMessagesFacade {
  def writeMessages: Flow[Try[Iterable[Serialized]], Try[Iterable[Serialized]], Unit] =
    Flow.fromGraph(GraphDSL.create() { implicit b ⇒
      import GraphDSL.Implicits._
      val broadcast = b.add(Broadcast[Try[Iterable[Serialized]]](2))
      val zip = b.add(Zip[Unit, Try[Iterable[Serialized]]]())

      broadcast.out(0).collect {
        case Success(xs) ⇒ xs
      }.mapAsync(1)(journalDao.writeList) ~> zip.in0
      broadcast.out(1) ~> zip.in1

      FlowShape(broadcast.in, zip.out)
    }).map {
      case (x, y) ⇒ y
    }
}

class SlickJournalDaoQueries(val profile: JdbcProfile, override val journalTableCfg: JournalTableConfiguration, override val deletedToTableCfg: DeletedToTableConfiguration) extends JournalTables {
  import profile.api._

  def writeList(xs: Iterable[Serialized]) =
    JournalTable ++= xs.map(ser ⇒ JournalRow(ser.persistenceId, ser.sequenceNr, ser.serialized.array()))
  //    JournalTable ++= xs.map(ser ⇒ JournalRow(ser.persistenceId, ser.sequenceNr, ser.serialized.array(), ser.created))

  def insertDeletedTo(persistenceId: String, highestSequenceNr: Option[Long]) =
    DeletedToTable += JournalDeletedToRow(persistenceId, highestSequenceNr.getOrElse(0L))

  def selectAllDeletedTo(persistenceId: String): Query[DeletedTo, JournalDeletedToRow, Seq] =
    DeletedToTable.filter(_.persistenceId === persistenceId)

  def selectAllJournalForPersistenceId(persistenceId: String): Query[Journal, JournalRow, Seq] =
    JournalTable.filter(_.persistenceId === persistenceId).sortBy(_.sequenceNumber.desc)

  def highestSequenceNrForPersistenceId(persistenceId: String): Rep[Option[Long]] =
    selectAllJournalForPersistenceId(persistenceId).map(_.sequenceNumber).max

  def selectByPersistenceIdAndMaxSequenceNumber(persistenceId: String, maxSequenceNr: Long): Query[Journal, JournalRow, Seq] =
    selectAllJournalForPersistenceId(persistenceId).filter(_.sequenceNumber <= maxSequenceNr)

  def highestSequenceNumberFromJournalForPersistenceIdFromSequenceNr(persistenceId: String, fromSequenceNr: Long): Rep[Option[Long]] =
    selectAllJournalForPersistenceId(persistenceId).filter(_.sequenceNumber >= fromSequenceNr).map(_.sequenceNumber).max

  def selectHighestSequenceNrFromDeletedTo(persistenceId: String): Rep[Option[Long]] =
    selectAllDeletedTo(persistenceId).map(_.deletedTo).max

  def allPersistenceIdsDistinct: Query[Rep[String], String, Seq] =
    JournalTable.map(_.persistenceId).distinct

  def journalRowByPersistenceIds(persistenceIds: Iterable[String]) = for {
    query ← JournalTable.map(_.persistenceId)
    if query inSetBind persistenceIds
  } yield query

  def messagesQuery(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long) =
    JournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNumber >= fromSequenceNr)
      .filter(_.sequenceNumber <= toSequenceNr)
      .sortBy(_.sequenceNumber.asc)
      .take(max)

  //  def eventsByTag(tag: String, offset: Long) =
  //    JournalTable.filter(_.tags like tag).sortBy(_.created.asc).drop(offset)
}

trait SlickJournalDao extends JournalDao {
  val profile: slick.driver.JdbcProfile

  import profile.api._

  implicit def ec: ExecutionContext

  implicit def mat: Materializer

  def writeMessagesFacade: WriteMessagesFacade

  def db: JdbcBackend#Database

  def journalTableCfg: JournalTableConfiguration

  def deletedToTableCfg: DeletedToTableConfiguration

  def queries: SlickJournalDaoQueries

  def writeList(xs: Iterable[Serialized]): Future[Unit] = for {
    _ ← db.run(queries.writeList(xs))
  } yield ()

  def writeFlow: Flow[Try[Iterable[Serialized]], Try[Iterable[Serialized]], Unit] =
    Flow[Try[Iterable[Serialized]]].via(writeMessagesFacade.writeMessages)

  override def delete(persistenceId: String, maxSequenceNr: Long): Future[Unit] = {
    val actions = (for {
      highestSequenceNr ← queries.highestSequenceNrForPersistenceId(persistenceId).result
      _ ← queries.selectByPersistenceIdAndMaxSequenceNumber(persistenceId, maxSequenceNr).delete
      _ ← queries.insertDeletedTo(persistenceId, highestSequenceNr)
    } yield ()).transactionally
    db.run(actions)
  }

  override def highestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    val actions = (for {
      seqNumFoundInJournalTable ← queries.highestSequenceNumberFromJournalForPersistenceIdFromSequenceNr(persistenceId, fromSequenceNr).result
      highestSeqNumberFoundInDeletedToTable ← queries.selectHighestSequenceNrFromDeletedTo(persistenceId).result
      highestSequenceNumber = seqNumFoundInJournalTable.getOrElse(highestSeqNumberFoundInDeletedToTable.getOrElse(0L))
    } yield highestSequenceNumber).transactionally
    db.run(actions)
  }

  override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[Array[Byte], Unit] =
    Source.fromPublisher(db.stream(queries.messagesQuery(persistenceId, fromSequenceNr, toSequenceNr, max).result)).map(_.message)

  override def persistenceIds(queryListOfPersistenceIds: Iterable[String]): Future[Seq[String]] = for {
    xs ← db.run(queries.journalRowByPersistenceIds(queryListOfPersistenceIds).result)
  } yield xs

  override def allPersistenceIdsSource: Source[String, Unit] =
    Source.fromPublisher(db.stream(queries.allPersistenceIdsDistinct.result))

  //  override def eventsByTag(tag: String, offset: Long): Source[Array[Byte], Unit] =
  //    Source.fromPublisher(db.stream(queries.eventsByTag(tag, offset).result)).map(_.message)
}

class JdbcSlickJournalDao(val db: JdbcBackend#Database, override val profile: JdbcProfile, override val journalTableCfg: JournalTableConfiguration, override val deletedToTableCfg: DeletedToTableConfiguration)(implicit val ec: ExecutionContext, val mat: Materializer) extends SlickJournalDao {
  override val writeMessagesFacade: WriteMessagesFacade = new FlowGraphWriteMessagesFacade(this)

  override val queries: SlickJournalDaoQueries = new SlickJournalDaoQueries(profile, journalTableCfg, deletedToTableCfg)
}