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

package akka.persistence.jdbc
package journal.dao

import akka.{ Done, NotUsed }
import akka.persistence.jdbc.config.JournalConfig
import akka.persistence.jdbc.serialization.FlowPersistentReprSerializer
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.serialization.Serialization
import akka.stream.scaladsl.{ Keep, Sink, Source }
import akka.stream.{ Materializer, OverflowStrategy, QueueOfferResult }
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend._
import slick.jdbc.{ JdbcProfile, H2Profile }

import scala.collection.immutable._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success, Try }

/**
 * The DefaultJournalDao contains all the knowledge to persist and load serialized journal entries
 */
trait BaseByteArrayJournalDao extends JournalDaoWithUpdates {

  val db: Database
  val profile: JdbcProfile
  val queries: JournalQueries
  val journalConfig: JournalConfig
  val serializer: FlowPersistentReprSerializer[JournalRow]
  implicit val ec: ExecutionContext
  implicit val mat: Materializer

  import journalConfig.daoConfig.{ batchSize, bufferSize, logicalDelete, parallelism }
  import profile.api._

  val logger = LoggerFactory.getLogger(this.getClass)

  // This logging may block since we don't control how the user will configure logback
  // We can't use a Akka logging neither because we don't have an ActorSystem in scope and
  // we should not introduce another dependency here.
  // Therefore, we make sure we only log a warning for logical deletes once
  lazy val logWarnAboutLogicalDeletionDeprecation = {
    logger.warn(
      "Logical deletion of events is deprecated and will be removed in akka-persistende-jdbc version 4.0.0. " +
        "To disable it in this current version you must set the property 'akka-persistence-jdbc.logicalDeletion.enable' to false.")
  }

  private val writeQueue = Source.queue[(Promise[Unit], Seq[JournalRow])](bufferSize, OverflowStrategy.dropNew)
    .batchWeighted[(Seq[Promise[Unit]], Seq[JournalRow])](batchSize, _._2.size, tup => Vector(tup._1) -> tup._2) {
      case ((promises, rows), (newPromise, newRows)) => (promises :+ newPromise) -> (rows ++ newRows)
    }.mapAsync(parallelism) {
      case (promises, rows) =>
        writeJournalRows(rows)
          .map(unit => promises.foreach(_.success(unit)))
          .recover { case t => promises.foreach(_.failure(t)) }
    }.toMat(Sink.ignore)(Keep.left).run()

  private def queueWriteJournalRows(xs: Seq[JournalRow]): Future[Unit] = {
    val promise = Promise[Unit]()
    writeQueue.offer(promise -> xs).flatMap {
      case QueueOfferResult.Enqueued =>
        promise.future
      case QueueOfferResult.Failure(t) =>
        Future.failed(new Exception("Failed to write journal row batch", t))
      case QueueOfferResult.Dropped =>
        Future.failed(new Exception(s"Failed to enqueue journal row batch write, the queue buffer was full ($bufferSize elements) please check the jdbc-journal.bufferSize setting"))
      case QueueOfferResult.QueueClosed =>
        Future.failed(new Exception("Failed to enqueue journal row batch write, the queue was closed"))
    }
  }

  private def writeJournalRows(xs: Seq[JournalRow]): Future[Unit] = {
    // Write atomically without auto-commit
    db.run(queries.writeJournalRows(xs).transactionally).map(_ => ())
  }

  /**
   * @see [[akka.persistence.journal.AsyncWriteJournal.asyncWriteMessages(messages)]]
   */
  def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = {
    val serializedTries = serializer.serialize(messages)

    // If serialization fails for some AtomicWrites, the other AtomicWrites may still be written
    val rowsToWrite = for {
      serializeTry <- serializedTries
      row <- serializeTry.getOrElse(Seq.empty)
    } yield row
    def resultWhenWriteComplete =
      if (serializedTries.forall(_.isSuccess)) Nil else serializedTries.map(_.map(_ => ()))

    queueWriteJournalRows(rowsToWrite).map(_ => resultWhenWriteComplete)
  }

  override def delete(persistenceId: String, maxSequenceNr: Long): Future[Unit] =
    if (logicalDelete) {
      // We only log a warning when user effectively deletes an event.
      // The rationale here is that this feature is not so broadly used and the default
      // is to have logical delete enabled.
      // We don't want to log warnings for users that are not using this,
      // so we make it happen only when effectively used.
      logWarnAboutLogicalDeletionDeprecation
      db.run(queries.markJournalMessagesAsDeleted(persistenceId, maxSequenceNr)).map(_ => ())
    } else {
      // We should keep journal record with highest sequence number in order to be compliant
      // with @see [[akka.persistence.journal.JournalSpec]]
      val actions = for {
        _ <- queries.markJournalMessagesAsDeleted(persistenceId, maxSequenceNr)
        highestMarkedSequenceNr <- highestMarkedSequenceNr(persistenceId)
        _ <- queries.delete(persistenceId, highestMarkedSequenceNr.getOrElse(0L) - 1)
      } yield ()

      db.run(actions.transactionally)
    }

  def update(persistenceId: String, sequenceNr: Long, payload: AnyRef): Future[Done] = {
    val write = PersistentRepr(payload, sequenceNr, persistenceId)
    val serializedRow = serializer.serialize(write) match {
      case Success(t)  => t
      case Failure(ex) => throw new IllegalArgumentException(s"Failed to serialize ${write.getClass} for update of [$persistenceId] @ [$sequenceNr]")
    }
    db.run(queries.update(persistenceId, sequenceNr, serializedRow.message).map(_ => Done))
  }

  private def highestMarkedSequenceNr(persistenceId: String) =
    queries.highestMarkedSequenceNrForPersistenceId(persistenceId).result.headOption

  override def highestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = for {
    maybeHighestSeqNo <- db.run(queries.highestSequenceNrForPersistenceId(persistenceId).result.headOption)
  } yield maybeHighestSeqNo.getOrElse(0L)

  override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[Try[PersistentRepr], NotUsed] =
    Source.fromPublisher(db.stream(queries.messagesQuery(persistenceId, fromSequenceNr, toSequenceNr, max).result))
      .via(serializer.deserializeFlowWithoutTags)
}

trait H2JournalDao extends JournalDao {
  val profile: JdbcProfile

  private lazy val isH2Driver = profile match {
    case slick.jdbc.H2Profile => true
    case _                    => false
  }

  abstract override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[Try[PersistentRepr], NotUsed] = {
    super.messages(persistenceId, fromSequenceNr, toSequenceNr, correctMaxForH2Driver(max))
  }

  private def correctMaxForH2Driver(max: Long): Long = {
    if (isH2Driver) {
      Math.min(max, Int.MaxValue) // H2 only accepts a LIMIT clause as an Integer
    } else {
      max
    }
  }
}

class ByteArrayJournalDao(val db: Database, val profile: JdbcProfile, val journalConfig: JournalConfig, serialization: Serialization)(implicit val ec: ExecutionContext, val mat: Materializer) extends BaseByteArrayJournalDao with H2JournalDao {
  val queries = new JournalQueries(profile, journalConfig.journalTableConfiguration)
  val serializer = new ByteArrayJournalSerializer(serialization, journalConfig.pluginConfig.tagSeparator)
}

