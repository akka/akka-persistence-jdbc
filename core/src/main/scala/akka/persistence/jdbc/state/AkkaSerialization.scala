/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.state

/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

import akka.annotation.InternalApi
import akka.serialization.{ Serialization, Serializers }

import scala.util.Try

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

  def fromRow(serialization: Serialization)(row: DurableStateTables.DurableStateRow): Try[AnyRef] = {
    serialization.deserialize(row.statePayload, row.stateSerId, row.stateSerManifest.getOrElse(""))
  }
}
