/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.journal.dao

import akka.annotation.InternalApi
import akka.persistence.jdbc.config.{ EventJournalTableConfiguration, EventTagTableConfiguration }
import akka.persistence.jdbc.journal.dao.JournalTables.{ JournalAkkaSerializationRow, TagRow }

/**
 * INTERNAL API
 */
@InternalApi
object JournalTables {
  case class JournalAkkaSerializationRow(
      ordering: Long,
      deleted: Boolean,
      persistenceId: String,
      sequenceNumber: Long,
      writer: String,
      writeTimestamp: Long,
      adapterManifest: String,
      eventPayload: Array[Byte],
      eventSerId: Int,
      eventSerManifest: String,
      metaPayload: Option[Array[Byte]],
      metaSerId: Option[Int],
      metaSerManifest: Option[String])

  case class TagRow(eventId: Option[Long], persistenceId: Option[String], sequenceNumber: Option[Long], tag: String)
}

/**
 * For the schema added in 5.0.0
 * INTERNAL API
 */
@InternalApi
trait JournalTables {
  val profile: slick.jdbc.JdbcProfile

  import profile.api._

  def journalTableCfg: EventJournalTableConfiguration
  def tagTableCfg: EventTagTableConfiguration

  class JournalEvents(_tableTag: Tag)
      extends Table[JournalAkkaSerializationRow](
        _tableTag,
        _schemaName = journalTableCfg.schemaName,
        _tableName = journalTableCfg.tableName) {
    def * =
      (
        ordering,
        deleted,
        persistenceId,
        sequenceNumber,
        writer,
        timestamp,
        adapterManifest,
        eventPayload,
        eventSerId,
        eventSerManifest,
        metaPayload,
        metaSerId,
        metaSerManifest) <> (JournalAkkaSerializationRow.tupled, JournalAkkaSerializationRow.unapply)

    val ordering: Rep[Long] = column[Long](journalTableCfg.columnNames.ordering, O.AutoInc)
    val persistenceId: Rep[String] =
      column[String](journalTableCfg.columnNames.persistenceId, O.Length(255, varying = true))
    val sequenceNumber: Rep[Long] = column[Long](journalTableCfg.columnNames.sequenceNumber)
    val deleted: Rep[Boolean] = column[Boolean](journalTableCfg.columnNames.deleted, O.Default(false))

    val writer: Rep[String] = column[String](journalTableCfg.columnNames.writer)
    val adapterManifest: Rep[String] = column[String](journalTableCfg.columnNames.adapterManifest)
    val timestamp: Rep[Long] = column[Long](journalTableCfg.columnNames.writeTimestamp)

    val eventPayload: Rep[Array[Byte]] = column[Array[Byte]](journalTableCfg.columnNames.eventPayload)
    val eventSerId: Rep[Int] = column[Int](journalTableCfg.columnNames.eventSerId)
    val eventSerManifest: Rep[String] = column[String](journalTableCfg.columnNames.eventSerManifest)

    val metaPayload: Rep[Option[Array[Byte]]] = column[Option[Array[Byte]]](journalTableCfg.columnNames.metaPayload)
    val metaSerId: Rep[Option[Int]] = column[Option[Int]](journalTableCfg.columnNames.metaSerId)
    val metaSerManifest: Rep[Option[String]] = column[Option[String]](journalTableCfg.columnNames.metaSerManifest)

    val pk = primaryKey(s"${tableName}_pk", (persistenceId, sequenceNumber))
    val orderingIdx = index(s"${tableName}_ordering_idx", ordering, unique = true)
  }

  lazy val JournalTable = new TableQuery(tag => new JournalEvents(tag))

  class EventTags(_tableTag: Tag) extends Table[TagRow](_tableTag, tagTableCfg.schemaName, tagTableCfg.tableName) {
    override def * = (eventId, persistenceId, sequenceNumber, tag) <> (TagRow.tupled, TagRow.unapply)
    // allow null value insert.
    val eventId: Rep[Option[Long]] = column[Long](tagTableCfg.columnNames.eventId)
    val persistenceId: Rep[Option[String]] = column[String](tagTableCfg.columnNames.persistenceId)
    val sequenceNumber: Rep[Option[Long]] = column[Long](tagTableCfg.columnNames.sequenceNumber)
    val tag: Rep[String] = column[String](tagTableCfg.columnNames.tag)

    val pk = primaryKey(s"${tagTableCfg.tableName}_pk", (persistenceId, sequenceNumber, tag))
    val journalEvent =
      foreignKey(s"fk_${journalTableCfg.tableName}", (persistenceId, sequenceNumber), JournalTable)(e =>
        (e.persistenceId, e.sequenceNumber))
  }

  lazy val TagTable = new TableQuery(tag => new EventTags(tag))
}
