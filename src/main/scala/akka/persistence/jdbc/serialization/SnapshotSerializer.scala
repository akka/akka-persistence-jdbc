package akka.persistence.jdbc.serialization

import akka.persistence.SnapshotMetadata

import scala.util.Try

trait SnapshotSerializer[T] {
  def serialize(metadata: SnapshotMetadata, snapshot: Any): Try[T]

  def deserialize(t: T): Try[(SnapshotMetadata, Any)]
}
