/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.serialization

import akka.NotUsed
import akka.persistence.jdbc.util.TrySeq
import akka.persistence.journal.Tagged
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.stream.scaladsl.Flow
import scala.collection.immutable._

import scala.util.Try

@deprecated("use Akka Serialization for the payloads instead", since = "5.0.0")
trait PersistentReprSerializer[T] {

  /**
   * An akka.persistence.AtomicWrite contains a Sequence of events (with metadata, the PersistentRepr)
   * that must all be persisted or all fail, what makes the operation atomic. The function converts
   * each AtomicWrite to a Try[Seq[T]].
   * The Try denotes whether there was a problem with the AtomicWrite or not.
   */
  def serialize(messages: Seq[AtomicWrite]): Seq[Try[Seq[T]]] = {
    messages.map { atomicWrite =>
      val serialized = atomicWrite.payload.map(serialize)
      TrySeq.sequence(serialized)
    }
  }

  def serialize(persistentRepr: PersistentRepr): Try[T] =
    persistentRepr.payload match {
      case Tagged(payload, tags) =>
        serialize(persistentRepr.withPayload(payload), tags)
      case _ => serialize(persistentRepr, Set.empty[String])
    }

  def serialize(persistentRepr: PersistentRepr, tags: Set[String]): Try[T]

  /**
   * deserialize into a PersistentRepr, a set of tags and a Long representing the global ordering of events
   */
  def deserialize(t: T): Try[(PersistentRepr, Set[String], Long)]
}

@deprecated("use Akka Serialization for the payloads instead", since = "5.0.0")
trait FlowPersistentReprSerializer[T] extends PersistentReprSerializer[T] {

  /**
   * A flow which deserializes each element into a PersistentRepr,
   * a set of tags and a Long representing the global ordering of events
   */
  def deserializeFlow: Flow[T, Try[(PersistentRepr, Set[String], Long)], NotUsed] = {
    Flow[T].map(deserialize)
  }

}
