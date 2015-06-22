package akka.persistence.jdbc.serialization.journal


import akka.persistence.PersistentRepr
import akka.persistence.jdbc.serialization.JournalTypeConverter
import akka.serialization.Serialization
import org.apache.commons.codec.binary.Base64

class Base64JournalConverter extends JournalTypeConverter {
  override def marshal(value: PersistentRepr)(implicit serialization: Serialization): String =
    serialization.serialize(value).map(new Base64().encodeToString).get

  override def unmarshal(value: String, persistenceId: String)(implicit serialization: Serialization): PersistentRepr =
    serialization.deserialize(new Base64().decode(value), classOf[PersistentRepr]).get
}
