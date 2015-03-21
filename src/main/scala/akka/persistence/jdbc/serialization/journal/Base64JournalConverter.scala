package akka.persistence.jdbc.serialization.journal

import java.util.Base64

import akka.persistence.PersistentRepr
import akka.persistence.jdbc.serialization.JournalTypeConverter
import akka.serialization.Serialization

class Base64JournalConverter extends JournalTypeConverter {
  override def marshal(value: PersistentRepr)(implicit serialization: Serialization): String =
    serialization.serialize(value).map(Base64.getEncoder.encodeToString).get

  override def unmarshal(value: String)(implicit serialization: Serialization): PersistentRepr =
    serialization.deserialize(Base64.getDecoder.decode(value), classOf[PersistentRepr]).get
}
