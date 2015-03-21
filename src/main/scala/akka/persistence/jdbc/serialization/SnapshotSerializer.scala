package akka.persistence.jdbc.serialization

import akka.persistence.serialization.Snapshot
import akka.serialization.Serialization

trait SnapshotSerializer {
  def unmarshal(value: String)(implicit conv: SnapshotTypeConverter, serialization: Serialization): Snapshot =
    conv.unmarshal(value)

  def marshal(value: Snapshot)(implicit conv: SnapshotTypeConverter, serialization: Serialization): String =
    conv.marshal(value)
}

