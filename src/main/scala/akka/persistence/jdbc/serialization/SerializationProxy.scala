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

import akka.NotUsed
import akka.actor.ActorSystem
import akka.persistence.journal.Tagged
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.serialization.{ Serialization, SerializationExtension }
import akka.stream.scaladsl.Flow

import scala.compat.Platform
import scala.util.{ Success, Try }

sealed trait SerializationResult {
  def persistenceId: String
  def sequenceNr: Long
  def tags: Option[String]
  def created: Long
}
final case class Serialized(persistenceId: String, sequenceNr: Long, serialized: Array[Byte], tags: Option[String] = None, created: Long = Platform.currentTime) extends SerializationResult
final case class NotSerialized(persistenceId: String, sequenceNr: Long, persistentRepr: PersistentRepr, tags: Option[String], created: Long = Platform.currentTime) extends SerializationResult

trait SerializationProxy {
  def serialize(o: AnyRef): Try[Array[Byte]]
  def deserialize[A](bytes: Array[Byte], clazz: Class[A]): Try[A]
}

object AkkaSerializationProxy {
  def apply(serialization: Serialization) =
    new AkkaSerializationProxy(serialization)
}

class AkkaSerializationProxy(serialization: Serialization) extends SerializationProxy {
  override def serialize(o: AnyRef): Try[Array[Byte]] = o match {
    // when you passed an Array[Byte] to be persisted,
    // you probably don't want to serialize the array
    case arr: Array[Byte] ⇒ Success(arr)
    case _                ⇒ serialization.serialize(o)
  }

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
  def apply(system: ActorSystem, separatorChar: String): SerializationFacade =
    new SerializationFacade(new AkkaSerializationProxy(SerializationExtension(system)), separatorChar)

  def encodeTags(tags: Set[String], separatorChar: String): Option[String] =
    if (tags.isEmpty) None else Option(tags.mkString(separatorChar))

  def decodeTags(tags: String, separator: String): List[String] =
    tags.split(separator).toList
}

class SerializationFacade(proxy: SerializationProxy, separator: String) {
  import SerializationFacade._

  def decodeTags(tags: String): List[String] =
    SerializationFacade.decodeTags(tags, separator)

  /**
   * Serializes an [[akka.persistence.AtomicWrite]]
   */
  private def serializeAtomicWrite(atomicWrite: AtomicWrite, serialize: Boolean): Try[Iterable[SerializationResult]] = {
    def serializeARepr(repr: PersistentRepr, tags: Set[String] = Set.empty[String]): Try[SerializationResult] =
      if (serialize) proxy.serialize(repr).map(byteArray ⇒ Serialized(repr.persistenceId, repr.sequenceNr, byteArray, encodeTags(tags, separator)))
      else Success(NotSerialized(repr.persistenceId, repr.sequenceNr, repr, encodeTags(tags, separator)))

    def serializeTaggedOrRepr(repr: PersistentRepr): Try[SerializationResult] = repr.payload match {
      case Tagged(payload, tags) ⇒
        serializeARepr(repr.withPayload(payload), tags)
      case _ ⇒ serializeARepr(repr)
    }

    val xs = atomicWrite.payload.map(serializeTaggedOrRepr)
    if (xs.exists(_.isFailure)) xs.filter(_.isFailure).head.map(Seq(_)) // SI-8566
    else Success(xs.foldLeft(List.empty[SerializationResult]) {
      case (xy, Success(serialized)) ⇒ xy :+ serialized
      case (xy, _)                   ⇒ xy
    })
  }

  /**
   * An akka.persistence.AtomicWrite contains a Sequence of events (with metadata, the PersistentRepr)
   * that must all be persisted or all fail, what makes the operation atomic. The flow converts
   * akka.persistence.AtomicWrite and converts them to a Try[Iterable[Serialized]]. The Try denotes
   * whether there was a problem with the AtomicWrite or not.
   */
  def serialize(serialize: Boolean): Flow[AtomicWrite, Try[Iterable[SerializationResult]], NotUsed] =
    Flow[AtomicWrite].map(aw ⇒ serializeAtomicWrite(aw, serialize))

  def persistentFromSerializationResult(serializationResult: SerializationResult): Try[PersistentRepr] = serializationResult match {
    case Serialized(_, _, bytes, _, _)   ⇒ proxy.deserialize(bytes, classOf[PersistentRepr])
    case NotSerialized(_, _, repr, _, _) ⇒ Success(repr)
  }

  def deserializeRepr: Flow[SerializationResult, Try[PersistentRepr], NotUsed] =
    Flow[SerializationResult].map(persistentFromSerializationResult)
}
