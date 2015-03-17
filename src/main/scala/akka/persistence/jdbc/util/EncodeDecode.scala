package akka.persistence.jdbc.util

import akka.persistence.serialization.Snapshot
import akka.persistence.PersistentRepr
import akka.serialization.Serialization

trait EncodeDecode {
  def serialization: Serialization

  object Journal {
    def toBytes(msg: PersistentRepr): Array[Byte] = serialization.serialize(msg).get

    def fromBytes(bytes: Array[Byte]): PersistentRepr = serialization.deserialize(bytes, classOf[PersistentRepr]).get
  }

  object Snapshot {
    def toBytes(msg: Snapshot): Array[Byte] = serialization.serialize(msg).get

    def fromBytes(bytes: Array[Byte]): Snapshot = serialization.deserialize(bytes, classOf[Snapshot]).get
  }
}
