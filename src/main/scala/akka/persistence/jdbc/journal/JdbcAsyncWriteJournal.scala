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

package akka.persistence.jdbc.journal

import java.util.{ HashMap => JHMap, Map => JMap }

import akka.Done
import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.persistence.jdbc.config.JournalConfig
import akka.persistence.jdbc.journal.JdbcAsyncWriteJournal.{ InPlaceUpdateEvent, WriteFinished }
import akka.persistence.jdbc.journal.dao.{ JournalDao, JournalDaoWithUpdates }
import akka.persistence.jdbc.util.{ SlickDatabase, SlickDriver }
import akka.persistence.journal.{ AsyncWriteJournal, Tagged }
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.serialization.{ Serialization, SerializationExtension }
import akka.stream.{ ActorMaterializer, Materializer }
import com.typesafe.config.Config
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcBackend._

import scala.collection.immutable._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }
import akka.pattern.pipe

object JdbcAsyncWriteJournal {
  private case class WriteFinished(pid: String, f: Future[_])
  /**
   * Extra Plugin API: May be used to issue in-place updates for events.
   * To be used only for data migrations such as "encrypt all events" and similar operations.
   *
   * The write payload may be wrapped in a [[akka.persistence.journal.Tagged]],
   * in which case the new tags will overwrite the existing tags of the event.
   */
  final case class InPlaceUpdateEvent(persistenceId: String, seqNr: Long, write: AnyRef)
}

class JdbcAsyncWriteJournal(config: Config) extends AsyncWriteJournal {
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val system: ActorSystem = context.system
  implicit val mat: Materializer = ActorMaterializer()
  val journalConfig = new JournalConfig(config)

  val db: Database = SlickDatabase.forConfig(config, journalConfig.slickConfiguration)

  val journalDao: JournalDao = {
    val fqcn = journalConfig.pluginConfig.dao
    val profile: JdbcProfile = SlickDriver.forDriverName(config)
    val args = Seq(
      (classOf[Database], db),
      (classOf[JdbcProfile], profile),
      (classOf[JournalConfig], journalConfig),
      (classOf[Serialization], SerializationExtension(system)),
      (classOf[ExecutionContext], ec),
      (classOf[Materializer], mat))
    system.asInstanceOf[ExtendedActorSystem].dynamicAccess.createInstanceFor[JournalDao](fqcn, args) match {
      case Success(dao)   => dao
      case Failure(cause) => throw cause
    }
  }
  // only accessed if we need to perform Updates -- which is very rarely
  def journalDaoWithUpdates: JournalDaoWithUpdates =
    journalDao match {
      case upgraded: JournalDaoWithUpdates => upgraded
      case _ => throw new IllegalStateException(s"The ${journalDao.getClass} does NOT implement [JournalDaoWithUpdates], " +
        s"which is required to perform updates of events! Please configure a valid update capable DAO (e.g. the default [ByteArrayJournalDao].")
    }

  // readHighestSequence must be performed after pending write for a persistenceId
  // when the persistent actor is restarted.
  private val writeInProgress: JMap[String, Future[_]] = new JHMap

  override def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = {
    val future = journalDao.asyncWriteMessages(messages)
    val persistenceId = messages.head.persistenceId
    writeInProgress.put(persistenceId, future)
    future.onComplete(_ => self ! WriteFinished(persistenceId, future))
    future
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] =
    journalDao.delete(persistenceId, toSequenceNr)

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    def fetchHighestSeqNr() = journalDao.highestSequenceNr(persistenceId, fromSequenceNr)
    writeInProgress.get(persistenceId) match {
      case null => fetchHighestSeqNr()
      case f =>
        // we must fetch the highest sequence number after the previous write has completed
        // If the previous write failed then we can ignore this
        f.recover { case _ => () }.flatMap(_ => fetchHighestSeqNr())
    }
  }

  private def asyncUpdateEvent(persistenceId: String, sequenceNr: Long, message: AnyRef): Future[Done] = {
    journalDaoWithUpdates.update(persistenceId, sequenceNr, message)
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(recoveryCallback: (PersistentRepr) => Unit): Future[Unit] =
    journalDao.messages(persistenceId, fromSequenceNr, toSequenceNr, max)
      .mapAsync(1)(deserializedRepr => Future.fromTry(deserializedRepr))
      .runForeach(recoveryCallback)
      .map(_ => ())

  override def postStop(): Unit = {
    db.close()
    super.postStop()
  }

  override def receivePluginInternal: Receive = {
    case WriteFinished(persistenceId, future) =>
      writeInProgress.remove(persistenceId, future)
    case InPlaceUpdateEvent(pid, seq, write) =>
      asyncUpdateEvent(pid, seq, write)
        .pipeTo(sender())
  }
}
