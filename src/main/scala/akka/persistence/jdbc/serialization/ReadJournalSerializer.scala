package akka.persistence.jdbc.serialization

import akka.NotUsed
import akka.persistence.jdbc.util.TrySeq
import akka.persistence.journal.Tagged
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.stream.scaladsl.Flow

import scala.util.Try


trait ReadJournalSerializer[T] {

  def serialize(persistentRepr: PersistentRepr): Try[T] = persistentRepr.payload match {
    case Tagged(payload, tags) ⇒
      serialize(persistentRepr.withPayload(payload), tags)
    case _ => serialize(persistentRepr, Set.empty[String])
  }

  def serialize(persistentRepr: PersistentRepr, tags: Set[String]): Try[T]

  def deserialize(t: T): Try[(PersistentRepr, Set[String])]

}

trait FlowReadJournalSerializer[T] extends ReadJournalSerializer[T] {

  /**
   * An akka.persistence.AtomicWrite contains a Sequence of events (with metadata, the PersistentRepr)
   * that must all be persisted or all fail, what makes the operation atomic. The flow converts
   * akka.persistence.AtomicWrite and converts them to a Try[Seq[T]].
   * The Try denotes whether there was a problem with the AtomicWrite or not.
   */
  def serializeFlow: Flow[AtomicWrite, Try[Seq[T]], NotUsed] = {
    Flow[AtomicWrite]
    .map(_.payload.map(serialize))
    .map(TrySeq.sequence[T])
  }

  def deserializeFlow: Flow[T, Try[(PersistentRepr, Set[String])], NotUsed] = {
    Flow[T].map(deserialize)
  }

  def deserializeFlowWithoutTags: Flow[T, Try[PersistentRepr], NotUsed] = {
    deserializeFlow.map(_.map(_._1))
  }
}