package akka.persistence.jdbc.serialization

import akka.NotUsed
import akka.persistence.jdbc.util.TrySeq
import akka.persistence.journal.Tagged
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.stream.scaladsl.Flow

import scala.util.Try


trait Serializer[T] {

  def serialize(persistentRepr: PersistentRepr, tags: Set[String] = Set.empty[String]): Try[T]

  private def serializeTaggedPayloads(persistentRepr: PersistentRepr): Try[T] = persistentRepr.payload match {
    case Tagged(payload, tags) ⇒
      serialize(persistentRepr.withPayload(payload), tags)
    case _ => serialize(persistentRepr)
  }

  def serializeAtomicWrite: Flow[AtomicWrite, Try[Seq[T]], NotUsed] = {
    Flow[AtomicWrite]
    .map(_.payload.map(serializeTaggedPayloads))
    .map(TrySeq.sequence[T])
  }

}
