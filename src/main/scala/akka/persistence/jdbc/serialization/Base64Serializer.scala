/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.jdbc.serialization

import akka.actor.ExtendedActorSystem
import akka.persistence.jdbc.serialization.Base64Serializer._
import akka.serialization._

object Base64Serializer {
  def encodeBase64(bytes: Array[Byte]): Array[Byte] =
    java.util.Base64.getUrlEncoder.encode(bytes)

  def decodeBase64(bytes: Array[Byte]): Array[Byte] =
    java.util.Base64.getUrlDecoder.decode(bytes)
}

/**
 * Encodes/decodes a PersistentRepr
 */
class Base64Serializer(val system: ExtendedActorSystem) extends BaseSerializer {
  override def includeManifest: Boolean = false

  /**
   * Assumes the received byte array is a Base64 encoded PersistentRepr
   */
  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    decodeBase64(bytes)

  /**
   * Assumes the received object is a Serialized PersistentRepr in either
   * String encoding or byte array encoding
   */
  override def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case str: String     ⇒ encodeBase64(str.getBytes("UTF-8"))
    case xs: Array[Byte] ⇒ encodeBase64(xs)
    case other           ⇒ throw new IllegalArgumentException("Base64Serializer only serializes byte arrays and strings not [" + other + "]")
  }

}
