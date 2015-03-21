package akka.persistence.jdbc.serialization

import akka.persistence.PersistentRepr
import akka.serialization.Serialization

trait JournalSerializer {
  def unmarshal(value: String)(implicit conv: JournalTypeConverter, serialization: Serialization): PersistentRepr =
    conv.unmarshal(value)

  def marshal(value: PersistentRepr)(implicit conv: JournalTypeConverter, serialization: Serialization): String =
    conv.marshal(value)
}
