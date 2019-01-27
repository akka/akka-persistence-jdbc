/*
 * Copyright 2016 Dennis Vriend
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

package akka.persistence.jdbc.snapshot.dao

import akka.persistence.jdbc.config.SnapshotTableConfiguration
import akka.persistence.jdbc.snapshot.dao.SnapshotTables.SnapshotRow
import slick.jdbc.JdbcProfile

class SnapshotQueries(val profile: JdbcProfile, override val snapshotTableCfg: SnapshotTableConfiguration) extends SnapshotTables {
  import profile.api._

  private val SnapshotTableC = Compiled(SnapshotTable)

  def insertOrUpdate(snapshotRow: SnapshotRow) =
    SnapshotTableC.insertOrUpdate(snapshotRow)

  private def _selectAll(persistenceId: Rep[String]) =
    SnapshotTable.filter(_.persistenceId === persistenceId).sortBy(_.sequenceNumber.desc)
  val selectAll = Compiled(_selectAll _)

  private def _selectLatestByPersistenceId(persistenceId: Rep[String]) =
    _selectAll(persistenceId).take(1)
  val selectLatestByPersistenceId = Compiled(_selectLatestByPersistenceId _)

  private def _selectByPersistenceIdAndSequenceNr(persistenceId: Rep[String], sequenceNr: Rep[Long]) =
    _selectAll(persistenceId).filter(_.sequenceNumber === sequenceNr)
  val selectByPersistenceIdAndSequenceNr = Compiled(_selectByPersistenceIdAndSequenceNr _)

  private def _selectByPersistenceIdUpToMaxTimestamp(persistenceId: Rep[String], maxTimestamp: Rep[Long]) =
    _selectAll(persistenceId).filter(_.created <= maxTimestamp)
  val selectByPersistenceIdUpToMaxTimestamp = Compiled(_selectByPersistenceIdUpToMaxTimestamp _)

  private def _selectByPersistenceIdUpToMaxSequenceNr(persistenceId: Rep[String], maxSequenceNr: Rep[Long]) =
    _selectAll(persistenceId).filter(_.sequenceNumber <= maxSequenceNr)
  val selectByPersistenceIdUpToMaxSequenceNr = Compiled(_selectByPersistenceIdUpToMaxSequenceNr _)

  private def _selectByPersistenceIdUpToMaxSequenceNrAndMaxTimestamp(persistenceId: Rep[String], maxSequenceNr: Rep[Long], maxTimestamp: Rep[Long]) =
    _selectByPersistenceIdUpToMaxSequenceNr(persistenceId, maxSequenceNr).filter(_.created <= maxTimestamp)
  val selectByPersistenceIdUpToMaxSequenceNrAndMaxTimestamp = Compiled(_selectByPersistenceIdUpToMaxSequenceNrAndMaxTimestamp _)

  private def _selectOneByPersistenceIdAndMaxTimestamp(persistenceId: Rep[String], maxTimestamp: Rep[Long]) =
    _selectAll(persistenceId).filter(_.created <= maxTimestamp).take(1)
  val selectOneByPersistenceIdAndMaxTimestamp = Compiled(_selectOneByPersistenceIdAndMaxTimestamp _)

  private def _selectOneByPersistenceIdAndMaxSequenceNr(persistenceId: Rep[String], maxSequenceNr: Rep[Long]) =
    _selectAll(persistenceId).filter(_.sequenceNumber <= maxSequenceNr).take(1)
  val selectOneByPersistenceIdAndMaxSequenceNr = Compiled(_selectOneByPersistenceIdAndMaxSequenceNr _)

  private def _selectOneByPersistenceIdAndMaxSequenceNrAndMaxTimestamp(persistenceId: Rep[String], maxSequenceNr: Rep[Long], maxTimestamp: Rep[Long]) =
    _selectByPersistenceIdUpToMaxSequenceNr(persistenceId, maxSequenceNr).filter(_.created <= maxTimestamp).take(1)
  val selectOneByPersistenceIdAndMaxSequenceNrAndMaxTimestamp = Compiled(_selectOneByPersistenceIdAndMaxSequenceNrAndMaxTimestamp _)
}
