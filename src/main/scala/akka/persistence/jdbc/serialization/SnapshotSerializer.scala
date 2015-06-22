package akka.persistence.jdbc.serialization

import akka.persistence.serialization.Snapshot
import akka.serialization.Serialization

trait SnapshotSerializer {
  def unmarshal(value: String, persistenceId: String)(implicit conv: SnapshotTypeConverter, serialization: Serialization): Snapshot =
    conv.unmarshal(value, persistenceId)

  def marshal(value: Snapshot)(implicit conv: SnapshotTypeConverter, serialization: Serialization): String =
    conv.marshal(value)
}

