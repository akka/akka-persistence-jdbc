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

import akka.persistence.jdbc.dao.SnapshotDao.SnapshotData
import akka.persistence.jdbc.dao.Tables._
import akka.persistence.jdbc.util.SlickDriver
import akka.stream.Materializer
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.concurrent.{ ExecutionContext, Future }

object SnapshotDao {

  case class SnapshotData(persistenceId: String, sequenceNumber: Long, created: Long, snapshot: Array[Byte])

  def apply(driver: String, db: JdbcBackend#Database)(implicit ec: ExecutionContext, mat: Materializer): SnapshotDao =
    if (SlickDriver.forDriverName.isDefinedAt(driver)) {
      new JdbcSlickSnapshotDao(db, SlickDriver.forDriverName(driver))
    } else throw new IllegalArgumentException("Unknown slick driver: " + driver)
}

trait SnapshotDao {
  def deleteAllSnapshots(persistenceId: String): Future[Unit]

  def deleteUpToMaxSequenceNr(persistenceId: String, maxSequenceNr: Long): Future[Unit]

  def deleteUpToMaxTimestamp(persistenceId: String, maxTimestamp: Long): Future[Unit]

  def deleteUpToMaxSequenceNrAndMaxTimestamp(persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long): Future[Unit]

  def snapshotForMaxSequenceNr(persistenceId: String): Future[Option[SnapshotData]]

  def snapshotForMaxTimestamp(persistenceId: String, timestamp: Long): Future[Option[SnapshotData]]

  def snapshotForMaxSequenceNr(persistenceId: String, sequenceNr: Long): Future[Option[SnapshotData]]

  def snapshotForMaxSequenceNrAndMaxTimestamp(persistenceId: String, sequenceNr: Long, timestamp: Long): Future[Option[SnapshotData]]

  def delete(persistenceId: String, sequenceNr: Long): Future[Unit]

  def save(persistenceId: String, sequenceNr: Long, timestamp: Long, snapshot: Array[Byte]): Future[Unit]
}

class SlickSnapshotDaoQueries(val profile: JdbcProfile) extends Tables {
  import profile.api._

  def maxSeqNrForPersistenceId(persistenceId: String) =
    selectAll(persistenceId).map(_.sequenceNumber).max

  def insertOrUpdate(persistenceId: String, sequenceNr: Long, created: Long, snapshot: Array[Byte]) =
    SnapshotTable.insertOrUpdate(SnapshotRow(persistenceId, sequenceNr, created, snapshot))

  def selectAll(persistenceId: String) =
    SnapshotTable.filter(_.persistenceId === persistenceId).sortBy(_.sequenceNumber.desc)

  def selectByPersistenceIdAndMaxSeqNr(persistenceId: String) =
    selectAll(persistenceId).filter(_.sequenceNumber === maxSeqNrForPersistenceId(persistenceId))

  def selectByPersistenceIdAndSeqNr(persistenceId: String, sequenceNr: Long) =
    selectAll(persistenceId).filter(_.sequenceNumber === sequenceNr)

  def selectByPersistenceIdAndMaxTimestamp(persistenceId: String, maxTimestamp: Long) =
    selectAll(persistenceId).filter(_.created <= maxTimestamp)

  def selectByPersistenceIdAndMaxSequenceNr(persistenceId: String, maxSequenceNr: Long) =
    selectAll(persistenceId).filter(_.sequenceNumber <= maxSequenceNr)

  def selectByPersistenceIdAndMaxSequenceNrAndMaxTimestamp(persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long) =
    selectByPersistenceIdAndMaxSequenceNr(persistenceId, maxSequenceNr).filter(_.created <= maxTimestamp)
}

trait SlickSnapshotDao extends SnapshotDao with Tables {

  import profile.api._

  implicit def ec: ExecutionContext

  implicit def mat: Materializer

  def db: JdbcBackend#Database

  def queries: SlickSnapshotDaoQueries

  def mapToSnapshotData(row: SnapshotRow): SnapshotData =
    SnapshotData(row.persistenceId, row.sequenceNumber, row.created, row.snapshot)

  override def snapshotForMaxSequenceNr(persistenceId: String): Future[Option[SnapshotData]] = for {
    rows ← db.run(queries.selectByPersistenceIdAndMaxSeqNr(persistenceId).result)
  } yield rows.headOption map mapToSnapshotData

  override def snapshotForMaxTimestamp(persistenceId: String, maxTimestamp: Long): Future[Option[SnapshotData]] = for {
    rows ← db.run(queries.selectByPersistenceIdAndMaxTimestamp(persistenceId, maxTimestamp).result)
  } yield rows.headOption map mapToSnapshotData

  override def snapshotForMaxSequenceNr(persistenceId: String, maxSequenceNr: Long): Future[Option[SnapshotData]] = for {
    rows ← db.run(queries.selectByPersistenceIdAndMaxSequenceNr(persistenceId, maxSequenceNr).result)
  } yield rows.headOption map mapToSnapshotData

  override def snapshotForMaxSequenceNrAndMaxTimestamp(persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long): Future[Option[SnapshotData]] = for {
    rows ← db.run(queries.selectByPersistenceIdAndMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp).result)
  } yield rows.headOption map mapToSnapshotData

  override def save(persistenceId: String, sequenceNr: Long, created: Long, snapshot: Array[Byte]): Future[Unit] = for {
    _ ← db.run(queries.insertOrUpdate(persistenceId, sequenceNr, created, snapshot))
  } yield ()

  override def delete(persistenceId: String, sequenceNr: Long): Future[Unit] = for {
    _ ← db.run(queries.selectByPersistenceIdAndSeqNr(persistenceId, sequenceNr).delete)
  } yield ()

  override def deleteAllSnapshots(persistenceId: String): Future[Unit] = for {
    _ ← db.run(queries.selectAll(persistenceId).delete)
  } yield ()

  override def deleteUpToMaxSequenceNr(persistenceId: String, maxSequenceNr: Long): Future[Unit] = for {
    _ ← db.run(queries.selectByPersistenceIdAndMaxSequenceNr(persistenceId, maxSequenceNr).delete)
  } yield ()

  override def deleteUpToMaxTimestamp(persistenceId: String, maxTimestamp: Long): Future[Unit] = for {
    _ ← db.run(queries.selectByPersistenceIdAndMaxTimestamp(persistenceId, maxTimestamp).delete)
  } yield ()

  override def deleteUpToMaxSequenceNrAndMaxTimestamp(persistenceId: String, maxSequenceNr: Long, maxTimestamp: Long): Future[Unit] = for {
    _ ← db.run(queries.selectByPersistenceIdAndMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp).delete)
  } yield ()
}

class JdbcSlickSnapshotDao(val db: JdbcBackend#Database, override val profile: JdbcProfile)(implicit val ec: ExecutionContext, val mat: Materializer) extends SlickSnapshotDao {
  override val queries: SlickSnapshotDaoQueries = new SlickSnapshotDaoQueries(profile)
}
