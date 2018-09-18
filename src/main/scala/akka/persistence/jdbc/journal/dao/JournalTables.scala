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

package akka.persistence.jdbc
package journal.dao

import akka.persistence.jdbc.config.JournalTableConfiguration
import slick.lifted.ProvenShape

trait JournalTables {
  val profile: slick.jdbc.JdbcProfile

  import profile.api._

  def journalTableCfg: JournalTableConfiguration

  class Journal(_tableTag: Tag) extends Table[JournalRow](_tableTag, _schemaName = journalTableCfg.schemaName, _tableName = journalTableCfg.tableName) {
    def * : ProvenShape[JournalRow] = if (journalTableCfg.hasMessageColumn) {
      (ordering, deleted, persistenceId, sequenceNumber, message, tags, event, eventManifest, serId, serManifest, writerUuid) <> (JournalRow.tupled, JournalRow.unapply)
    } else {
      (ordering, deleted, persistenceId, sequenceNumber, tags, event, eventManifest, serId, serManifest, writerUuid) <> ({
        case (ordering, deleted, persistenceId, sequenceNumber, tags, event, eventManifest, serId, serManifest, writerUuid) =>
          JournalRow(ordering, deleted, persistenceId, sequenceNumber, None, tags, event, eventManifest, serId, serManifest, writerUuid)
      },
        (row: JournalRow) => {
          Some((row.ordering, row.deleted, row.persistenceId, row.sequenceNumber, row.tags, row.event, row.eventManifest, row.serId, row.serManifest, row.writerUuid))
        })
    }

    val ordering: Rep[Long] = column[Long](journalTableCfg.columnNames.ordering, O.AutoInc)
    val persistenceId: Rep[String] = column[String](journalTableCfg.columnNames.persistenceId, O.Length(255, varying = true))
    val sequenceNumber: Rep[Long] = column[Long](journalTableCfg.columnNames.sequenceNumber)
    val deleted: Rep[Boolean] = column[Boolean](journalTableCfg.columnNames.deleted, O.Default(false))
    val tags: Rep[Option[String]] = column[Option[String]](journalTableCfg.columnNames.tags, O.Length(255, varying = true))
    @deprecated("This was used to store the the PersistentRepr serialized as is, but no longer.", "4.0.0")
    private[dao] val message: Rep[Option[Array[Byte]]] = column[Option[Array[Byte]]](journalTableCfg.columnNames.message)

    val eventManifest: Rep[Option[String]] = if (journalTableCfg.hasMessageColumn)
      column[Option[String]](journalTableCfg.columnNames.eventManifest, O.Length(255, varying = true))
    else
      Rep.Some(column[String](journalTableCfg.columnNames.eventManifest, O.Length(255, varying = true)))

    val event: Rep[Option[Array[Byte]]] =
      if (journalTableCfg.hasMessageColumn) column[Option[Array[Byte]]](journalTableCfg.columnNames.event)
      else Rep.Some(column[Array[Byte]](journalTableCfg.columnNames.event))

    val serId: Rep[Option[Int]] =
      if (journalTableCfg.hasMessageColumn) column[Option[Int]](journalTableCfg.columnNames.serId)
      else Rep.Some(column[Int](journalTableCfg.columnNames.serId))

    val writerUuid: Rep[Option[String]] =
      if (journalTableCfg.hasMessageColumn) column[Option[String]](journalTableCfg.columnNames.writerUuid, O.Length(36, varying = true))
      else Rep.Some(column[String](journalTableCfg.columnNames.writerUuid, O.Length(36, varying = true)))

    val serManifest: Rep[Option[String]] = if (journalTableCfg.hasMessageColumn)
      column[Option[String]](journalTableCfg.columnNames.serManifest, O.Length(255, varying = true))
    else
      Rep.Some(column[String](journalTableCfg.columnNames.serManifest, O.Length(255, varying = true)))

    val pk = primaryKey(s"${tableName}_pk", (persistenceId, sequenceNumber))
    val orderingIdx = index(s"${tableName}_ordering_idx", ordering, unique = true)
  }

  lazy val JournalTable = new TableQuery(tag => new Journal(tag))
}
