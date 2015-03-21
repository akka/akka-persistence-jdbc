package akka.persistence.jdbc.serialization

import akka.persistence.PersistentRepr
import akka.serialization.Serialization

trait JournalTypeConverter {
  def marshal(value: PersistentRepr)(implicit serialization: Serialization): String

  def unmarshal(value: String)(implicit serialization: Serialization): PersistentRepr
}
