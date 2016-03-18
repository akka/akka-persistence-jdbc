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

package akka.persistence.jdbc.query.journal.scaladsl

import akka.NotUsed
import akka.actor.{ ExtendedActorSystem, Props }
import akka.persistence.jdbc.dao.JournalDao
import akka.persistence.jdbc.extension.{ AkkaPersistenceConfig, DaoRepository }
import akka.persistence.jdbc.query.journal.publisher.{ AllPersistenceIdsPublisher, EventsByPersistenceIdAndTagPublisher, EventsByPersistenceIdPublisher, EventsByTagPublisher }
import akka.persistence.jdbc.serialization.{ AkkaSerializationProxy, SerializationFacade }
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl._
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.Source
import akka.stream.{ ActorMaterializer, Materializer }
import com.typesafe.config.Config

import scala.concurrent.Future

object JdbcReadJournal {
  final val Identifier = "jdbc-read-journal"
}

trait SlickReadJournal extends ReadJournal
    with CurrentPersistenceIdsQuery
    with AllPersistenceIdsQuery
    with CurrentEventsByPersistenceIdQuery
    with EventsByPersistenceIdQuery
    with CurrentEventsByTagQuery
    with EventsByTagQuery
    with CurrentEventsByPersistenceIdAndTagQuery
    with EventsByPersistenceIdAndTagQuery {

  implicit def mat: Materializer

  def journalDao: JournalDao

  def serializationFacade: SerializationFacade

  def akkaPersistenceConfiguration: AkkaPersistenceConfig

  override def currentPersistenceIds(): Source[String, NotUsed] =
    journalDao.allPersistenceIdsSource

  override def allPersistenceIds(): Source[String, NotUsed] =
    currentPersistenceIds()
      .concat(Source.actorPublisher[String](Props(classOf[AllPersistenceIdsPublisher])))

  override def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    journalDao.messages(persistenceId, fromSequenceNr, toSequenceNr, Long.MaxValue)
      .via(serializationFacade.deserializeRepr)
      .mapAsync(1)(deserializedRepr ⇒ Future.fromTry(deserializedRepr))
      .map(repr ⇒ EventEnvelope(repr.sequenceNr, repr.persistenceId, repr.sequenceNr, repr.payload))

  override def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr)
      .concat(Source.actorPublisher[EventEnvelope](Props(classOf[EventsByPersistenceIdPublisher], persistenceId)))

  override def currentEventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    journalDao.eventsByTag(tag, offset)
      .via(serializationFacade.deserializeRepr)
      .mapAsync(1)(deserializedRepr ⇒ Future.fromTry(deserializedRepr))
      .zipWith(Source(Stream.from(offset.toInt + 1))) { // Needs a better way
        case (repr, i) ⇒ EventEnvelope(i, repr.persistenceId, repr.sequenceNr, repr.payload)
      }

  override def eventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    currentEventsByTag(tag, offset)
      .concat(Source.actorPublisher[EventEnvelope](Props(classOf[EventsByTagPublisher], tag)))
      .zipWith(Source(Stream.from(offset.toInt + 1))) { // Needs a better way
        case (orig, i) ⇒ orig.copy(offset = i)
      }

  override def currentEventsByPersistenceIdAndTag(persistenceId: String, tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    journalDao.eventsByPersistenceIdAndTag(persistenceId, tag, offset)
      .via(serializationFacade.deserializeRepr)
      .mapAsync(1)(deserializedRepr ⇒ Future.fromTry(deserializedRepr))
      .zipWith(Source(Stream.from(offset.toInt + 1))) { // Needs a better way
        case (repr, i) ⇒ EventEnvelope(i, repr.persistenceId, repr.sequenceNr, repr.payload)
      }

  override def eventsByPersistenceIdAndTag(persistenceId: String, tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    currentEventsByPersistenceIdAndTag(persistenceId, tag, offset)
      .concat(Source.actorPublisher[EventEnvelope](Props(classOf[EventsByPersistenceIdAndTagPublisher], persistenceId, tag)))
      .zipWith(Source(Stream.from(offset.toInt + 1))) { // Needs a better way
        case (orig, i) ⇒ orig.copy(offset = i)
      }
}

class JdbcReadJournal(config: Config)(implicit val system: ExtendedActorSystem) extends SlickReadJournal {

  override implicit val mat: Materializer =
    ActorMaterializer()

  override val journalDao: JournalDao =
    DaoRepository(system).journalDao

  override val akkaPersistenceConfiguration: AkkaPersistenceConfig =
    AkkaPersistenceConfig(system)

  override val serializationFacade: SerializationFacade =
    new SerializationFacade(
      new AkkaSerializationProxy(SerializationExtension(system)),
      AkkaPersistenceConfig(system).persistenceQueryConfiguration.separator
    )
}
