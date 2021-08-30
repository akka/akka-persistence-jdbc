/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc

import akka.annotation.InternalApi
import akka.persistence.PersistentRepr
import akka.persistence.jdbc.state.DurableStateTables
import akka.persistence.jdbc.journal.dao.JournalTables.JournalAkkaSerializationRow
import akka.serialization.{ Serialization, Serializers }

import scala.util.{ Success, Try }

/**
 * INTERNAL API
 */
@InternalApi
object AkkaSerialization {

  case class AkkaSerialized(serId: Int, serManifest: String, payload: Array[Byte])

  def serialize(serialization: Serialization, payload: Any): Try[AkkaSerialized] = {
    val p2 = payload.asInstanceOf[AnyRef]
    val serializer = serialization.findSerializerFor(p2)
    val serManifest = Serializers.manifestFor(serializer, p2)
    val serialized = serialization.serialize(p2)
    serialized.map(payload => AkkaSerialized(serializer.identifier, serManifest, payload))
  }

  def fromRow(serialization: Serialization)(row: JournalAkkaSerializationRow): Try[(PersistentRepr, Long)] = {
    serialization.deserialize(row.eventPayload, row.eventSerId, row.eventSerManifest).flatMap { payload =>

      val metadata = for {
        mPayload <- row.metaPayload
        mSerId <- row.metaSerId
      } yield (mPayload, mSerId)

      val repr = PersistentRepr(
        payload,
        row.sequenceNumber,
        row.persistenceId,
        row.adapterManifest,
        row.deleted,
        sender = null,
        writerUuid = row.writer)

      // This means that failure to deserialize the meta will fail the read, I think this is the correct to do
      for {
        withMeta <- metadata match {
          case None => Success(repr)
          case Some((payload, id)) =>
            serialization.deserialize(payload, id, row.metaSerManifest.getOrElse("")).map { meta =>
              repr.withMetadata(meta)
            }
        }
      } yield (withMeta.withTimestamp(row.writeTimestamp), row.ordering)
    }
  }

  def fromDurableStateRow(serialization: Serialization)(row: DurableStateTables.DurableStateRow): Try[AnyRef] = {
    serialization.deserialize(row.statePayload, row.stateSerId, row.stateSerManifest.getOrElse(""))
  }
}
