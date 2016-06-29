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
import akka.actor.{ ExtendedActorSystem, Props }
import akka.persistence.jdbc.config.ReadJournalConfig
import akka.persistence.jdbc.dao.ReadJournalDao
import akka.persistence.jdbc.query.{ AllPersistenceIdsPublisher, EventsByPersistenceIdPublisher, EventsByTagPublisher }
import akka.persistence.jdbc.util.{ SlickDatabase, SlickDriver }
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl._
import akka.serialization.{ Serialization, SerializationExtension }
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
    val fqcn = readJournalConfig.pluginConfig.dao
    val profile: JdbcProfile = SlickDriver.forDriverName(config)
    val args = immutable.Seq(
      (classOf[Database], db),
      (classOf[JdbcProfile], profile),
      (classOf[ReadJournalConfig], readJournalConfig),
      (classOf[Serialization], SerializationExtension(system)),
      (classOf[ExecutionContext], ec),
      (classOf[Materializer], mat)
    )
    system.asInstanceOf[ExtendedActorSystem].dynamicAccess.createInstanceFor[ReadJournalDao](fqcn, args).get
  }

  override def currentPersistenceIds(): Source[String, NotUsed] =
    readJournalDao.allPersistenceIdsSource(Long.MaxValue)

  override def allPersistenceIds(): Source[String, NotUsed] =
    Source.actorPublisher[String](Props(new AllPersistenceIdsPublisher(readJournalDao, readJournalConfig.refreshInterval, readJournalConfig.maxBufferSize))).mapMaterializedValue(_ ⇒ NotUsed)

  override def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    readJournalDao.messages(persistenceId, fromSequenceNr, toSequenceNr, Long.MaxValue)
      .mapAsync(1)(deserializedRepr ⇒ Future.fromTry(deserializedRepr))
      .map(repr ⇒ EventEnvelope(repr.sequenceNr, repr.persistenceId, repr.sequenceNr, repr.payload))

  override def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] =
    Source.actorPublisher[EventEnvelope](Props(new EventsByPersistenceIdPublisher(persistenceId, fromSequenceNr, toSequenceNr, readJournalDao, readJournalConfig.refreshInterval, readJournalConfig.maxBufferSize))).mapMaterializedValue(_ ⇒ NotUsed)

  override def currentEventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    readJournalDao.eventsByTag(tag, offset, Long.MaxValue)
      .mapAsync(1)(Future.fromTry)
      .map {
        case (repr, _, row) ⇒ EventEnvelope(row.ordering, repr.persistenceId, repr.sequenceNr, repr.payload)
      }

  override def eventsByTag(tag: String, offset: Long): Source[EventEnvelope, NotUsed] =
    Source.actorPublisher[EventEnvelope](Props(new EventsByTagPublisher(tag, offset.toInt, readJournalDao, readJournalConfig.refreshInterval, readJournalConfig.maxBufferSize))).mapMaterializedValue(_ ⇒ NotUsed)
}
