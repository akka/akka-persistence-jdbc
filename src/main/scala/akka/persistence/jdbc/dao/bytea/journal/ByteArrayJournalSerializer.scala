package akka.persistence.jdbc.dao.bytea.journal

import akka.persistence.PersistentRepr
import akka.persistence.jdbc.dao.bytea.journal.JournalTables.JournalRow
import akka.persistence.jdbc.serialization.FlowPersistentReprSerializer
import akka.serialization.Serialization

import scala.compat.Platform
import scala.util.Try

object ByteArrayJournalSerializer {
  def encodeTags(tags: Set[String], separator: String): Option[String] =
    if (tags.isEmpty) None else Option(tags.mkString(separator))

  def decodeTags(tags: Option[String], separator: String): Set[String] =
    tags.map(_.split(separator).toSet).getOrElse(Set.empty[String])
}

class ByteArrayJournalSerializer(serialization: Serialization,
                                 separator: String) extends FlowPersistentReprSerializer[JournalRow] {

  override def serialize(persistentRepr: PersistentRepr, tags: Set[String]): Try[JournalRow] = {
    serialization
    .serialize(persistentRepr)
    .map(JournalRow(persistentRepr.persistenceId,
                    persistentRepr.sequenceNr,
                    _,
                    Platform.currentTime,
                    ByteArrayJournalSerializer.encodeTags(tags, separator)))
  }

  override def deserialize(journalRow: JournalRow): Try[(PersistentRepr, Set[String])] = {
    serialization.deserialize(journalRow.message, classOf[PersistentRepr])
      .map((_, ByteArrayJournalSerializer.decodeTags(journalRow.tags, separator)))
  }
}
