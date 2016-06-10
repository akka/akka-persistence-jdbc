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

package akka.persistence.jdbc.query.scaladsl

import akka.NotUsed
import akka.actor.ExtendedActorSystem
import akka.persistence.jdbc.config.ReadJournalConfig
import akka.persistence.jdbc.dao.ReadJournalDao
import akka.persistence.jdbc.serialization.SerializationFacade
import akka.persistence.jdbc.util.{ SlickDatabase, SlickDriver }
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl._
import akka.stream.scaladsl.Source
import akka.stream.{ ActorMaterializer, Materializer }
import com.typesafe.config.Config
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend._

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }

object JdbcReadJournal {
  final val Identifier = "jdbc-read-journal"
}

class JdbcReadJournal(config: Config)(implicit val system: ExtendedActorSystem) extends ReadJournal
    with CurrentPersistenceIdsQuery
    with AllPersistenceIdsQuery
    with CurrentEventsByPersistenceIdQuery
    with EventsByPersistenceIdQuery
    with CurrentEventsByTagQuery
    with EventsByTagQuery {

  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = ActorMaterializer()
  val readJournalConfig = new ReadJournalConfig(config)
  val db = SlickDatabase.forConfig(config, readJournalConfig.slickConfiguration)
  sys.addShutdownHook(db.close())

  val readJournalDao: ReadJournalDao = {
    val driver = readJournalConfig.slickConfiguration.slickDriver
    val fqcn = readJournalConfig.pluginConfig.dao
    val profile: JdbcProfile = SlickDriver.forDriverName(driver)
    val args = immutable.Seq(
      (classOf[Database], db),
      (classOf[JdbcProfile], profile),
      (classOf[ReadJournalConfig], readJournalConfig),
      (classOf[ExecutionContext], ec),
      (classOf[Materializer], mat)
    )
    system.asInstanceOf[ExtendedActorSystem].dynamicAccess.createInstanceFor[ReadJournalDao](fqcn, args).get
  }

  val serializationFacade: SerializationFacade =
    SerializationFacade(system, readJournalConfig.pluginConfig.tagSeparator)

  override def currentPersistenceIds(): Source[String, NotUsed] =
    readJournalDao.allPersistenceIdsSource

  override def allPersistenceIds(): Source[String, NotUsed] =
    currentPersistenceIds()

  override def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    readJournalDao.messages(persistenceId, fromSequenceNr, toSequenceNr, Long.MaxValue)
      .via(serializationFacade.deserializeRepr)
      .mapAsync(1)(deserializedRepr ⇒ Future.fromTry(deserializedRepr))
      .map(repr ⇒ EventEnvelope(repr.sequenceNr, repr.persistenceId, repr.sequenceNr, repr.payload))

  override def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr)

  override def currentEventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    readJournalDao.eventsByTag(tag, offset)
      .via(serializationFacade.deserializeRepr)
      .mapAsync(1)(deserializedRepr ⇒ Future.fromTry(deserializedRepr))
      .zipWith(Source(Stream.from(Math.max(1, offset.toInt)))) { // Needs a better way
        case (repr, i) ⇒ EventEnvelope(i, repr.persistenceId, repr.sequenceNr, repr.payload)
      }

  override def eventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    currentEventsByTag(tag, offset)
}
