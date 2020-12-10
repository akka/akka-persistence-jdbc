package akka.persistence.jdbc.journal.dao

import akka.persistence.PersistentRepr
import akka.persistence.jdbc.journal.dao.JournalTables.JournalAkkaSerializationRow
import akka.serialization.Serialization

import scala.util.Try

object AkkaSerialization {
  def fromRow(serialization: Serialization)(row: JournalAkkaSerializationRow): Try[(PersistentRepr, Long)] = {
    serialization.deserialize(row.eventPayload, row.eventSerId, row.eventSerManifest).map { payload =>
      // TODO support metadata
      val repr = PersistentRepr(
        payload,
        row.sequenceNumber,
        row.persistenceId,
        row.eventManifest,
        row.deleted,
        sender = null,
        writerUuid = row.writer)

      (repr.withTimestamp(row.writeTimestamp), row.ordering)
    }
  }
}
