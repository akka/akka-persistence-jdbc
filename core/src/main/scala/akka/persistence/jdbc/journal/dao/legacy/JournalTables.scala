/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.journal.dao.legacy

import akka.persistence.jdbc.config.LegacyJournalTableConfiguration

trait JournalTables {
  val profile: slick.jdbc.JdbcProfile

  import profile.api._

  def journalTableCfg: LegacyJournalTableConfiguration

  class Journal(_tableTag: Tag)
      extends Table[JournalRow](
        _tableTag,
        _schemaName = journalTableCfg.schemaName,
        _tableName = journalTableCfg.tableName) {
    def * = (ordering, deleted, persistenceId, sequenceNumber, message, tags) <> ((JournalRow.apply _).tupled, JournalRow.unapply)

    val ordering: Rep[Long] = column[Long](journalTableCfg.columnNames.ordering, O.AutoInc)
    val persistenceId: Rep[String] =
      column[String](journalTableCfg.columnNames.persistenceId, O.Length(255, varying = true))
    val sequenceNumber: Rep[Long] = column[Long](journalTableCfg.columnNames.sequenceNumber)
    val deleted: Rep[Boolean] = column[Boolean](journalTableCfg.columnNames.deleted, O.Default(false))
    val tags: Rep[Option[String]] =
      column[Option[String]](journalTableCfg.columnNames.tags, O.Length(255, varying = true))
    val message: Rep[Array[Byte]] = column[Array[Byte]](journalTableCfg.columnNames.message)
    val pk = primaryKey(s"${tableName}_pk", (persistenceId, sequenceNumber))
    val orderingIdx = index(s"${tableName}_ordering_idx", ordering, unique = true)
  }

  lazy val JournalTable = new TableQuery(tag => new Journal(tag))
}
