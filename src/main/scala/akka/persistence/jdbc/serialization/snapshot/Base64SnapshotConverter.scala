package akka.persistence.jdbc.serialization.snapshot


import akka.persistence.jdbc.serialization.SnapshotTypeConverter
import akka.persistence.serialization.Snapshot
import akka.serialization.Serialization
import org.apache.commons.codec.binary.Base64

class Base64SnapshotConverter extends SnapshotTypeConverter {
   override def marshal(value: Snapshot)(implicit serialization: Serialization): String =
     serialization.serialize(value).map(new Base64().encodeToString).get

   override def unmarshal(value: String)(implicit serialization: Serialization): Snapshot =
     serialization.deserialize(new Base64().decode(value), classOf[Snapshot]).get
 }
