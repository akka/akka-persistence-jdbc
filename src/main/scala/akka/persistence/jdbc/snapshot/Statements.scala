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

package akka.persistence.jdbc.snapshot

import java.io.StringReader
import java.sql.PreparedStatement

import akka.persistence.jdbc.common.PluginConfig
import akka.persistence.jdbc.serialization.{ SnapshotSerializer, SnapshotTypeConverter }
import akka.persistence.serialization.Snapshot
import akka.persistence.{ SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria }
import akka.serialization.Serialization
import scalikejdbc._

import scala.concurrent.ExecutionContext
import scala.util.Try

trait JdbcStatements {
  def deleteSnapshot(metadata: SnapshotMetadata): Unit

  def deleteSnapshots(persistenceId: String, criteria: SnapshotSelectionCriteria): Unit

  def writeSnapshot(metadata: SnapshotMetadata, snapshot: Snapshot): Unit

  def selectSnapshotFor(persistenceId: String, criteria: SnapshotSelectionCriteria): Option[SelectedSnapshot]
}

trait GenericStatements extends JdbcStatements with SnapshotSerializer {
  implicit val executionContext: ExecutionContext
  implicit val session: DBSession
  val cfg: PluginConfig

  val schema = cfg.snapshotSchemaName
  val table = cfg.snapshotTableName

  implicit def snapshotConverter: SnapshotTypeConverter

  implicit def serialization: Serialization

  override def deleteSnapshot(metadata: SnapshotMetadata): Unit =
    SQL(s"DELETE FROM $schema$table WHERE persistence_id = ? AND sequence_nr = ?")
      .bind(metadata.persistenceId, metadata.sequenceNr).update().apply()

  override def deleteSnapshots(persistenceId: String, criteria: SnapshotSelectionCriteria): Unit = criteria match {
    case SnapshotSelectionCriteria(Long.MaxValue, Long.MaxValue) ⇒
      SQL(s"DELETE FROM $schema$table WHERE persistence_id = ?").bind(persistenceId).update().apply()

    case SnapshotSelectionCriteria(upToSeqNo, Long.MaxValue) ⇒
      SQL(s"DELETE FROM $schema$table WHERE persistence_id = ? AND sequence_nr <= ?").bind(persistenceId, upToSeqNo).update().apply()

    case SnapshotSelectionCriteria(Long.MaxValue, maxTimeStamp) ⇒
      SQL(s"DELETE FROM $schema$table WHERE persistence_id = ? AND created <= ?").bind(persistenceId, maxTimeStamp).update().apply()

    case SnapshotSelectionCriteria(upToSeqNo, maxTimeStamp) ⇒
      SQL(s"DELETE FROM $schema$table WHERE persistence_id = ? AND sequence_nr <= ? AND created <= ?").bind(persistenceId, upToSeqNo, maxTimeStamp).update().apply()
  }

  def writeSnapshot(metadata: SnapshotMetadata, snapshot: Snapshot): Unit = {
    import metadata._
    Try {
      SQL(s"INSERT INTO $schema$table (persistence_id, sequence_nr, created, snapshot) VALUES (?, ?, ?, ?)")
        .bind(persistenceId, sequenceNr, timestamp, marshal(snapshot)).update().apply
    } recover {
      case ex: Exception ⇒ SQL(s"UPDATE $schema$table SET snapshot = ?, created = ? WHERE persistence_id = ? AND sequence_nr = ?")
        .bind(marshal(snapshot), timestamp, persistenceId, sequenceNr).update().apply
    }
  }

  def selectSnapshotFor(persistenceId: String, criteria: SnapshotSelectionCriteria): Option[SelectedSnapshot] = {
    def marshalSelectedSnapshot(rs: WrappedResultSet, persistenceId: String): SelectedSnapshot =
      SelectedSnapshot(SnapshotMetadata(rs.string("persistence_id"), rs.long("sequence_nr"), rs.long("created")), unmarshal(rs.string("snapshot"), persistenceId).data)

    criteria match {
      case SnapshotSelectionCriteria(Long.MaxValue, Long.MaxValue) ⇒
        SQL(s"SELECT * FROM $schema$table WHERE persistence_id = ? AND sequence_nr = (SELECT MAX(sequence_nr) FROM $schema$table WHERE persistence_id = ?)")
          .bind(persistenceId, persistenceId)
          .map(marshalSelectedSnapshot(_, persistenceId))
          .single()
          .apply()

      case SnapshotSelectionCriteria(maxSeqNo, Long.MaxValue) ⇒
        SQL(s"SELECT * FROM $schema$table WHERE persistence_id = ? AND sequence_nr = ? ORDER BY sequence_nr DESC")
          .bind(persistenceId, maxSeqNo)
          .map(marshalSelectedSnapshot(_, persistenceId))
          .single()
          .apply()

      case SnapshotSelectionCriteria(Long.MaxValue, maxTimestamp) ⇒
        SQL(s"SELECT * FROM $schema$table WHERE persistence_id = ? AND created <= ? ORDER BY sequence_nr DESC")
          .bind(persistenceId, maxTimestamp)
          .map(marshalSelectedSnapshot(_, persistenceId))
          .single()
          .apply()

      case SnapshotSelectionCriteria(maxSeqNo, maxTimestamp) ⇒
        SQL(s"SELECT * FROM $schema$table WHERE persistence_id = ? AND sequence_nr <= ? AND created <= ? ORDER BY sequence_nr DESC")
          .fetchSize(1)
          .bind(persistenceId, maxSeqNo, maxTimestamp)
          .map(marshalSelectedSnapshot(_, persistenceId))
          .list()
          .apply()
          .headOption
    }
  }
}

trait PostgresqlStatements extends GenericStatements

trait MySqlStatements extends GenericStatements {
  override def writeSnapshot(metadata: SnapshotMetadata, snapshot: Snapshot): Unit = {
    val snapshotData = marshal(snapshot)
    import metadata._
    SQL(
      s"""
         |INSERT INTO $schema$table (persistence_id, sequence_nr, created, snapshot)
         | VALUES (?, ?, ?, ?)
         | ON DUPLICATE KEY UPDATE snapshot = (?)""".stripMargin
    ).bind(persistenceId, sequenceNr, timestamp, snapshotData, snapshotData).update().apply
  }
}

trait H2Statements extends GenericStatements

trait OracleStatements extends GenericStatements {
  override def writeSnapshot(metadata: SnapshotMetadata, snapshot: Snapshot): Unit = {
    import metadata._

    DB autoCommit { session ⇒

      val clobBinder = ParameterBinder[StringReader](
        value = new StringReader(marshal(snapshot)),
        binder = (stmt: PreparedStatement, idx: Int) ⇒
          stmt.setClob(idx, new StringReader(marshal(snapshot)))
      )

      SQL(s"""MERGE INTO $schema$table snapshot
              USING (SELECT {persistenceId} AS persistence_id, {sequenceNr} AS seq_nr from DUAL) val
              ON (snapshot.persistence_id = val.persistence_id and snapshot.sequence_nr = val.seq_nr)
              WHEN MATCHED THEN
                UPDATE SET snapshot={snap}
              WHEN NOT MATCHED THEN
                INSERT (PERSISTENCE_ID, SEQUENCE_NR, SNAPSHOT, CREATED) VALUES ({persistenceId}, {sequenceNr}, {snap}, {created})""")
        .bindByName('persistenceId -> persistenceId, 'sequenceNr -> sequenceNr, 'created -> timestamp, 'snap -> clobBinder).execute().apply()(session)
    }
  }
}

trait MSSqlServerStatements extends GenericStatements

trait DB2Statements extends GenericStatements

trait InformixStatements extends GenericStatements
