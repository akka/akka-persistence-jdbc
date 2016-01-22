/*
 * Copyright 2015 Dennis Vriend
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

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.persistence.journal.Tagged
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.serialization.{ Serialization, SerializationExtension }
import akka.stream.scaladsl.Flow

import scala.compat.Platform
import scala.util.{ Failure, Success, Try }

case class Serialized(persistenceId: String, sequenceNr: Long, serialized: ByteBuffer, tags: Option[String] = None, created: Long = Platform.currentTime)

trait SerializationProxy {
  def serialize(o: AnyRef): Try[Array[Byte]]
  def deserialize[A](bytes: Array[Byte], clazz: Class[A]): Try[A]
}

object AkkaSerializationProxy {
  def apply(serialization: Serialization) =
    new AkkaSerializationProxy(serialization)
}

class AkkaSerializationProxy(serialization: Serialization) extends SerializationProxy {
  override def serialize(o: AnyRef): Try[Array[Byte]] =
    serialization.serialize(o)

  override def deserialize[A](bytes: Array[Byte], clazz: Class[A]): Try[A] =
    serialization.deserialize(bytes, clazz)
}

/**
 * We need to preserve the order / size of this sequence
 * We must NOT catch serialization exceptions here because rejections will cause
 * holes in the sequence number series and we use the sequence numbers to detect
 * missing (delayed) events in the eventByTag query
 *
 * Returns the serialized PersistentRepr, all fields will be serialized using
 * akka serialization which means that *all* fields of the PersistentRepr
 * including the payload. Note that when there is no serializer configured for
 * the type of the payload, the default Java Serializer will be used, which may
 * cause problems in the future,
 *
 * see: http://doc.akka.io/docs/akka/2.4.1/scala/persistence-schema-evolution.html
 *
 */

object SerializationFacade {
  def apply(system: ActorSystem, tagPrefix: String): SerializationFacade =
    new SerializationFacade(new AkkaSerializationProxy(SerializationExtension(system)), tagPrefix)

  def encodeTags(tags: Set[String], tagPrefix: String): Option[String] =
    if (tags.isEmpty) None else Option(tagPrefix + tags.mkString(tagPrefix) + tagPrefix)
}

class SerializationFacade(proxy: SerializationProxy, tagPrefix: String) {
  import SerializationFacade._
  private def serializeAtomicWrite(atomicWrite: AtomicWrite): Try[Iterable[Serialized]] = {
    def serializeARepr(repr: PersistentRepr, tags: Set[String] = Set.empty[String]): Try[Serialized] = for {
      byteArray ← proxy.serialize(repr)
    } yield Serialized(repr.persistenceId, repr.sequenceNr, ByteBuffer.wrap(byteArray), encodeTags(tags, tagPrefix))

    def serializeTaggedOrRepr(repr: PersistentRepr): Try[Serialized] = repr.payload match {
      case Tagged(payload, tags) ⇒
        serializeARepr(repr.withPayload(payload), tags)
      case _ ⇒ serializeARepr(repr)
    }

    val xs = atomicWrite.payload.map(serializeTaggedOrRepr)
    if (xs.exists(_.isFailure)) Failure(new RuntimeException("Could not serialize: " + atomicWrite))
    else Success(xs.foldLeft(List.empty[Serialized]) {
      case (xy, Success(serialized)) ⇒ xy :+ serialized
      case (xy, _)                   ⇒ xy
    })
  }

  def serialize: Flow[AtomicWrite, Try[Iterable[Serialized]], Unit] =
    Flow[AtomicWrite].map(serializeAtomicWrite)

  def persistentFromByteArray(bytes: Array[Byte]): Try[PersistentRepr] =
    proxy.deserialize(bytes, classOf[PersistentRepr])

  def deserializeRepr: Flow[Array[Byte], Try[PersistentRepr], Unit] =
    Flow[Array[Byte]].map(persistentFromByteArray)
}
