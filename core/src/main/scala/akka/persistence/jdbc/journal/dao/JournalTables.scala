package akka.persistence.jdbc.journal.dao

import akka.persistence.jdbc.config.JournalTableConfiguration
import akka.persistence.jdbc.journal.dao.JournalTables.JournalAkkaSerializationRow

object JournalTables {
  case class JournalAkkaSerializationRow(
      ordering: Long,
      deleted: Boolean, // TODO, remove? Or do we need it for something
      persistenceId: String,
      sequenceNumber: Long,
      writer: String,
      eventManifest: String,
      eventPayload: Array[Byte],
      eventSerId: Int,
      eventSerManifest: String,
      metaPayload: Option[Array[Byte]],
      metaSerId: Option[Int],
      metaSerManifest: Option[String])
}

/**
 * For the schema dded in 5.0.0
 */
trait JournalTables {
  val profile: slick.jdbc.JdbcProfile

  import profile.api._

  def journalTableCfg: JournalTableConfiguration

  class Journal(_tableTag: Tag)
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
        eventManifest,
        eventPayload,
        eventSerId,
        eventSerManifest,
        metaPayload,
        metaSerId,
        metaSerManifest) <> (JournalAkkaSerializationRow.tupled, JournalAkkaSerializationRow.unapply)

    val ordering: Rep[Long] = column[Long]("ordering", O.AutoInc)
    val persistenceId: Rep[String] =
      column[String]("persistence_id", O.Length(255, varying = true))
    val sequenceNumber: Rep[Long] = column[Long]("sequence_number")
    val deleted: Rep[Boolean] = column[Boolean]("deleted", O.Default(false))

    val writer: Rep[String] = column[String]("writer")
    val eventManifest: Rep[String] = column[String]("event_manifest")

    val eventPayload: Rep[Array[Byte]] = column[Array[Byte]]("event_payload")
    val eventSerId: Rep[Int] = column[Int]("event_ser_id")
    val eventSerManifest: Rep[String] = column[String]("event_ser_manifest")

    val metaPayload: Rep[Option[Array[Byte]]] = column[Option[Array[Byte]]]("meta_payload")
    val metaSerId: Rep[Option[Int]] = column[Option[Int]]("meta_ser_id")
    val metaSerManifest: Rep[Option[String]] = column[Option[String]]("meta_ser_manifest")

    val pk = primaryKey(s"${tableName}_pk", (persistenceId, sequenceNumber))
    val orderingIdx = index(s"${tableName}_ordering_idx", ordering, unique = true)
  }

  lazy val JournalTable = new TableQuery(tag => new Journal(tag))
}
