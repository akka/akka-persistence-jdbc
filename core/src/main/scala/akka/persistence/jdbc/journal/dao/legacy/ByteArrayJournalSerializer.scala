/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc
package journal.dao.legacy

import akka.persistence.PersistentRepr
import akka.persistence.jdbc.serialization.FlowPersistentReprSerializer
import akka.serialization.Serialization

import scala.annotation.nowarn
import scala.collection.immutable._
import scala.util.Try

@nowarn("msg=deprecated")
class ByteArrayJournalSerializer(serialization: Serialization, separator: String)
    extends FlowPersistentReprSerializer[JournalRow] {
  override def serialize(persistentRepr: PersistentRepr, tags: Set[String]): Try[JournalRow] = {
    serialization
      .serialize(persistentRepr)
      .map(
        JournalRow(
          Long.MinValue,
          persistentRepr.deleted,
          persistentRepr.persistenceId,
          persistentRepr.sequenceNr,
          _,
          encodeTags(tags, separator)))
  }

  override def deserialize(journalRow: JournalRow): Try[(PersistentRepr, Set[String], Long)] = {
    serialization
      .deserialize(journalRow.message, classOf[PersistentRepr])
      .map((_, decodeTags(journalRow.tags, separator), journalRow.ordering))
  }
}
