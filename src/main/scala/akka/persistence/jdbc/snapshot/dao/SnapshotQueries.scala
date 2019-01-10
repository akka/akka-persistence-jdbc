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

  private def _selectByPersistenceIdAndMaxSeqNr(persistenceId: Rep[String]) =
    _selectAll(persistenceId).take(1)
  val selectByPersistenceIdAndMaxSeqNr = Compiled(_selectByPersistenceIdAndMaxSeqNr _)

  private def _selectByPersistenceIdAndSeqNr(persistenceId: Rep[String], sequenceNr: Rep[Long]) =
    _selectAll(persistenceId).filter(_.sequenceNumber === sequenceNr)
  val selectByPersistenceIdAndSeqNr = Compiled(_selectByPersistenceIdAndSeqNr _)

  private def _selectByPersistenceIdAndMaxTimestamp(persistenceId: Rep[String], maxTimestamp: Rep[Long]) =
    _selectAll(persistenceId).filter(_.created <= maxTimestamp).take(1)
  val selectByPersistenceIdAndMaxTimestamp = Compiled(_selectByPersistenceIdAndMaxTimestamp _)

  private def _selectByPersistenceIdAndMaxSequenceNr(persistenceId: Rep[String], maxSequenceNr: Rep[Long]) =
    _selectAll(persistenceId).filter(_.sequenceNumber <= maxSequenceNr).take(1)
  val selectByPersistenceIdAndMaxSequenceNr = Compiled(_selectByPersistenceIdAndMaxSequenceNr _)

  private def _selectByPersistenceIdAndMaxSequenceNrAndMaxTimestamp(persistenceId: Rep[String], maxSequenceNr: Rep[Long], maxTimestamp: Rep[Long]) =
    _selectByPersistenceIdAndMaxSequenceNr(persistenceId, maxSequenceNr).filter(_.created <= maxTimestamp).take(1)
  val selectByPersistenceIdAndMaxSequenceNrAndMaxTimestamp = Compiled(_selectByPersistenceIdAndMaxSequenceNrAndMaxTimestamp _)
}
