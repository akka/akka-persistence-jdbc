package akka.persistence.jdbc.serialization

import akka.NotUsed
import akka.persistence.jdbc.util.TrySeq
import akka.persistence.journal.Tagged
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.stream.scaladsl.Flow

import scala.util.Try


trait Serializer[T] {

  def serialize(persistentRepr: PersistentRepr): Try[T] = persistentRepr.payload match {
    case Tagged(payload, tags) ⇒
      serialize(persistentRepr.withPayload(payload), tags)
    case _ => serialize(persistentRepr, Set.empty[String])
  }

  def serialize(persistentRepr: PersistentRepr, tags: Set[String]): Try[T]

}

trait FlowSerializer[T] extends Serializer[T] {
  def serializeFlow: Flow[AtomicWrite, Try[Seq[T]], NotUsed] = {
    Flow[AtomicWrite]
    .map(_.payload.map(serialize))
    .map(TrySeq.sequence[T])
  }
}