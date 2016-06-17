package akka.persistence.jdbc.dao.bytea

import akka.persistence.PersistentRepr
import akka.persistence.jdbc.dao.bytea.JournalTables.JournalRow
import akka.persistence.jdbc.serialization.Serializer
import akka.serialization.Serialization

import scala.compat.Platform
import scala.util.Try

object ByteArrayJournalSerializer {
  def encodeTags(tags: Set[String], separator: String): Option[String] =
    if (tags.isEmpty) None else Option(tags.mkString(separator))

  def decodeTags(tags: String, separator: String): Set[String] =
    tags.split(separator).toSet
}

class ByteArrayJournalSerializer(serialization: Serialization,
                                 separator: String) extends Serializer[JournalRow] {

  override def serialize(persistentRepr: PersistentRepr, tags: Set[String]): Try[JournalRow] = {
    serialization
    .serialize(persistentRepr)
    .map(JournalRow(persistentRepr.persistenceId,
                    persistentRepr.sequenceNr,
                    _,
                    Platform.currentTime,
                    ByteArrayJournalSerializer.encodeTags(tags, separator)))
  }
}
