package akka.persistence.jdbc.util

import akka.persistence.{serialization, PersistentRepr, Persistent}
import akka.serialization.{Serialization, SerializationExtension}
import org.apache.commons.codec.binary.{Base64 => B64}

object Base64 {
  /**
   * See: http://stackoverflow.com/questions/13412386/why-are-private-val-and-private-final-val-different
   * A val in Scala is already final in the Java sense. It looks like Scala's designers are using the redundant modifier
   * final to mean "permission to inline the constant value". So Scala programmers have complete control over this behavior
   * without resorting to hacks: if they want an inlined constant, a value that should never change but is fast, they write
   * "final val". if they want flexibility to change the value without breaking binary compatibility, just "val".
   */
  final private val b64 = new B64

  /** Encodes the given String into a Base64 String. **/
  def encodeString(in: String): String = encodeString(in.getBytes("UTF-8"))

  /** Encodes the given ByteArray into a Base64 String. **/
  def encodeString(in: Array[Byte]): String = new String(b64.encode(in))

  /** Encodes the given String into a Base64 ByteArray. **/
  def encodeBinary(in: String): Array[Byte] = b64.encode(in.getBytes("UTF-8"))

  /** Encodes the given ByteArray into a Base64 ByteArray. **/
  def encodeBinary(in: Array[Byte]): Array[Byte] = b64.encode(in)

  /** Decodes the given Base64-ByteArray into a String. **/
  def decodeString(in: Array[Byte]): String = new String(decodeBinary(in))

  /** Decodes the given Base64-String into a String. **/
  def decodeString(in: String): String = decodeString(in.getBytes("UTF-8"))

  /** Decodes the given Base64-String into a ByteArray. **/
  def decodeBinary(in: String): Array[Byte] = decodeBinary(in.getBytes("UTF-8"))

  /** Decodes the given Base64-ByteArray into a ByteArray. **/
  def decodeBinary(in: Array[Byte]): Array[Byte] = (new B64).decode(in)
}

trait EncodeDecode {
  def serialization: Serialization

  def toBytes(msg: Persistent): Array[Byte] = serialization.serialize(msg).get

  def fromBytes(bytes: Array[Byte]): PersistentRepr = serialization.deserialize(bytes, classOf[PersistentRepr]).get
}
