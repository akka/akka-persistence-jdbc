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

import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.persistence.jdbc.serialization.Base64Serializer._
import akka.serialization._

object Base64Serializer {
  final val JavaSerializerId = 1

  def serializeObject(obj: AnyRef)(implicit system: ActorSystem): Array[Byte] =
    SerializationExtension(system).serializerByIdentity(JavaSerializerId).toBinary(obj)

  def deserializeObject(bytes: Array[Byte])(implicit system: ActorSystem): AnyRef =
    SerializationExtension(system).serializerByIdentity(JavaSerializerId).fromBinary(bytes)

  def encodeBase64(bytes: Array[Byte]): Array[Byte] =
    java.util.Base64.getUrlEncoder.encode(bytes)

  def decodeBase64(bytes: Array[Byte]): Array[Byte] =
    java.util.Base64.getUrlDecoder.decode(bytes)
}


/**
 * The Base64 serializer must be used in conjunction with the JavaSerializer
 */
class Base64Serializer(val system: ExtendedActorSystem) extends BaseSerializer {
  implicit val theActorSystem = system

  override def includeManifest: Boolean = false

  /**
   * Decodes a Base64 encoded byte array and then deserializes
   */
  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    (decodeBase64 _ andThen deserializeObject _)(bytes)

  /**
   * Serializes a Java object and then encodes it to Base64
   */
  override def toBinary(obj: AnyRef): Array[Byte] =
    (serializeObject _ andThen encodeBase64 _)(obj)
}
