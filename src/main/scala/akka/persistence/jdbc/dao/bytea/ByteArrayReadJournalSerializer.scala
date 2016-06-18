package akka.persistence.jdbc.dao.bytea

import akka.persistence.PersistentRepr
import akka.persistence.jdbc.dao.bytea.ReadJournalTables.JournalRow
import akka.persistence.jdbc.serialization.FlowReadJournalSerializer
import akka.serialization.Serialization

import scala.compat.Platform
import scala.util.Try

object ByteArrayReadJournalSerializer {
  def encodeTags(tags: Set[String], separator: String): Option[String] =
    if (tags.isEmpty) None else Option(tags.mkString(separator))

  def decodeTags(tags: Option[String], separator: String): Set[String] =
    tags.map(_.split(separator).toSet).getOrElse(Set.empty[String])
}

class ByteArrayReadJournalSerializer(serialization: Serialization,
                                     separator: String) extends FlowReadJournalSerializer[JournalRow] {

  override def serialize(persistentRepr: PersistentRepr, tags: Set[String]): Try[JournalRow] = {
    serialization
    .serialize(persistentRepr)
    .map(JournalRow(persistentRepr.persistenceId,
                    persistentRepr.sequenceNr,
                    _,
                    Platform.currentTime,
                    ByteArrayReadJournalSerializer.encodeTags(tags, separator)))
  }

  override def deserialize(journalRow: JournalRow): Try[(PersistentRepr, Set[String])] = {
    serialization.deserialize(journalRow.message, classOf[PersistentRepr])
    .map((_, ByteArrayReadJournalSerializer.decodeTags(journalRow.tags, separator)))
  }
}