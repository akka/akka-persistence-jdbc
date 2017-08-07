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
import akka.persistence.jdbc.util.TrySeq
import akka.persistence.journal.Tagged
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.stream.scaladsl.Flow
import scala.collection.immutable._

import scala.util.Try

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

  def serialize(persistentRepr: PersistentRepr): Try[T] = persistentRepr.payload match {
    case Tagged(payload, tags) =>
      serialize(persistentRepr.withPayload(payload), tags)
    case _ => serialize(persistentRepr, Set.empty[String])
  }

  def serialize(persistentRepr: PersistentRepr, tags: Set[String]): Try[T]

  def deserialize(t: T): Try[(PersistentRepr, Set[String], T)]

}

trait FlowPersistentReprSerializer[T] extends PersistentReprSerializer[T] {

  def deserializeFlow: Flow[T, Try[(PersistentRepr, Set[String], T)], NotUsed] = {
    Flow[T].map(deserialize)
  }

  def deserializeFlowWithoutTags: Flow[T, Try[PersistentRepr], NotUsed] = {
    def keepPersistentRepr(tup: (PersistentRepr, Set[String], T)): PersistentRepr = tup match {
      case (repr, _, _) => repr
    }
    deserializeFlow.map(_.map(keepPersistentRepr))
  }
}
