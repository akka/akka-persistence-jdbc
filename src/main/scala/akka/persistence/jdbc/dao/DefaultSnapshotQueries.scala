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

package akka.persistence.jdbc.dao

import akka.persistence.jdbc.dao.SnapshotTables.SnapshotRow
import akka.persistence.jdbc.extension.SnapshotTableConfiguration
import slick.driver.JdbcProfile

class DefaultSnapshotQueries(val profile: JdbcProfile, override val snapshotTableCfg: SnapshotTableConfiguration) extends SnapshotTables {
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
