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

package akka.persistence.jdbc.query.journal

import akka.actor.{ ExtendedActorSystem, Props }
import akka.persistence.jdbc.dao.JournalDao
import akka.persistence.jdbc.extension.DaoRepository
import akka.persistence.jdbc.serialization.{ AkkaSerializationProxy, SerializationFacade }
import akka.persistence.query.scaladsl._
import akka.persistence.query.{ EventEnvelope, ReadJournalProvider }
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
    with CurrentEventsByPersistenceIdQuery {

  implicit def mat: Materializer

  def journalDao: JournalDao

  def serializationFacade: SerializationFacade

  override def currentPersistenceIds(): Source[String, Unit] =
    journalDao.allPersistenceIdsSource

  override def allPersistenceIds(): Source[String, Unit] =
    journalDao.allPersistenceIdsSource.concat(Source.actorPublisher[String](Props(classOf[AllPersistenceIdsPublisher], true)))

  override def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, Unit] =
    journalDao.messages(persistenceId, fromSequenceNr, toSequenceNr, Long.MaxValue)
      .via(serializationFacade.deserializeRepr)
      .mapAsync(1)(deserializedRepr ⇒ Future.fromTry(deserializedRepr))
      .map(repr ⇒ EventEnvelope(repr.sequenceNr, repr.persistenceId, repr.sequenceNr, repr.payload))
}

trait JdbcReadJournal extends SlickReadJournal

class PostgresReadJournal(config: Config)(implicit val system: ExtendedActorSystem) extends JdbcReadJournal {

  override implicit val mat: Materializer = ActorMaterializer()

  override val journalDao: JournalDao = DaoRepository(system).journalDao

  override val serializationFacade: SerializationFacade =
    new SerializationFacade(new AkkaSerializationProxy(SerializationExtension(system)))
}

class JdbcReadJournalProvider(system: ExtendedActorSystem, config: Config) extends ReadJournalProvider {
  override val scaladslReadJournal = new PostgresReadJournal(config)(system)

  override val javadslReadJournal = null
}