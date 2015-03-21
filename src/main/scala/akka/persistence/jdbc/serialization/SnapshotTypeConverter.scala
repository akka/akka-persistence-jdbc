package akka.persistence.jdbc.serialization

import akka.persistence.serialization.Snapshot
import akka.serialization.Serialization

trait SnapshotTypeConverter {
  def marshal(value: Snapshot)(implicit serialization: Serialization): String

  def unmarshal(value: String)(implicit serialization: Serialization): Snapshot
}
