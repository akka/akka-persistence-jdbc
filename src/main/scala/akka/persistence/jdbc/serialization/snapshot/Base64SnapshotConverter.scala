package akka.persistence.jdbc.serialization.snapshot

import java.util.Base64

import akka.persistence.jdbc.serialization.SnapshotTypeConverter
import akka.persistence.serialization.Snapshot
import akka.serialization.Serialization

class Base64SnapshotConverter extends SnapshotTypeConverter {
   override def marshal(value: Snapshot)(implicit serialization: Serialization): String =
     serialization.serialize(value).map(Base64.getEncoder.encodeToString).get

   override def unmarshal(value: String)(implicit serialization: Serialization): Snapshot =
     serialization.deserialize(Base64.getDecoder.decode(value), classOf[Snapshot]).get
 }
