package akka.persistence.jdbc.journal.dao

import scala.collection.immutable.Set

package object legacy {
  final case class JournalRow(
      ordering: Long,
      deleted: Boolean,
      persistenceId: String,
      sequenceNumber: Long,
      message: Array[Byte],
      tags: Option[String] = None)

  def encodeTags(tags: Set[String], separator: String): Option[String] =
    if (tags.isEmpty) None else Option(tags.mkString(separator))

  def decodeTags(tags: Option[String], separator: String): Set[String] =
    tags.map(_.split(separator).toSet).getOrElse(Set.empty[String])
}
