package akka.persistence.jdbc
package journal.dao.legacy

import akka.actor.Scheduler
import akka.{ Done, NotUsed }
import akka.persistence.jdbc.config.{ BaseDaoConfig, JournalConfig }
import akka.persistence.jdbc.journal.dao.{ FlowControl, JournalDao, JournalDaoWithReadMessages, JournalDaoWithUpdates }
import akka.persistence.jdbc.serialization.FlowPersistentReprSerializer
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.serialization.Serialization
import akka.stream.scaladsl.{ Keep, Sink, Source, SourceQueueWithComplete }
import akka.stream.{ Materializer, OverflowStrategy, QueueOfferResult }
import akka.{ Done, NotUsed }
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.JdbcProfile

import scala.collection.immutable.{ Nil, Seq, Vector }
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success, Try }

trait BaseJournalDaoWithReadMessages extends JournalDaoWithReadMessages {
  import FlowControl._

  implicit val ec: ExecutionContext
  implicit val mat: Materializer

  override def messagesWithBatch(
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long,
      batchSize: Int,
      refreshInterval: Option[(FiniteDuration, Scheduler)]): Source[Try[(PersistentRepr, Long)], NotUsed] = {

    Source
      .unfoldAsync[(Long, FlowControl), Seq[Try[(PersistentRepr, Long)]]]((Math.max(1, fromSequenceNr), Continue)) {
        case (from, control) =>
          def retrieveNextBatch(): Future[Option[((Long, FlowControl), Seq[Try[(PersistentRepr, Long)]])]] = {
            for {
              xs <- messages(persistenceId, from, toSequenceNr, batchSize).runWith(Sink.seq)
            } yield {
              val hasMoreEvents = xs.size == batchSize
              // Events are ordered by sequence number, therefore the last one is the largest)
              val lastSeqNrInBatch: Option[Long] = xs.lastOption match {
                case Some(Success((repr, _))) => Some(repr.sequenceNr)
                case Some(Failure(e))         => throw e // fail the returned Future
                case None                     => None
              }
              val hasLastEvent = lastSeqNrInBatch.exists(_ >= toSequenceNr)
              val nextControl: FlowControl =
                if (hasLastEvent || from > toSequenceNr) Stop
                else if (hasMoreEvents) Continue
                else if (refreshInterval.isEmpty) Stop
                else ContinueDelayed

              val nextFrom: Long = lastSeqNrInBatch match {
                // Continue querying from the last sequence number (the events are ordered)
                case Some(lastSeqNr) => lastSeqNr + 1
                case None            => from
              }
              Some((nextFrom, nextControl), xs)
            }
          }

          control match {
            case Stop     => Future.successful(None)
            case Continue => retrieveNextBatch()
            case ContinueDelayed =>
              val (delay, scheduler) = refreshInterval.get
              akka.pattern.after(delay, scheduler)(retrieveNextBatch())
          }
      }
      .mapConcat(identity)
  }

}

trait H2JournalDao extends JournalDao {
  val profile: JdbcProfile

  private lazy val isH2Driver = profile match {
    case slick.jdbc.H2Profile => true
    case _                    => false
  }

  abstract override def messages(
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long,
      max: Long): Source[Try[(PersistentRepr, Long)], NotUsed] = {
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

class ByteArrayJournalDao(
    val db: Database,
    val profile: JdbcProfile,
    val journalConfig: JournalConfig,
    serialization: Serialization)(implicit val ec: ExecutionContext, val mat: Materializer)
    extends BaseByteArrayJournalDao
    with H2JournalDao {
  val queries = new JournalQueries(profile, journalConfig.journalTableConfiguration)
  val serializer = new ByteArrayJournalSerializer(serialization, journalConfig.pluginConfig.tagSeparator)
}

trait BaseDao[T] {
  implicit val mat: Materializer
  implicit val ec: ExecutionContext

  def baseDaoConfig: BaseDaoConfig

  lazy val writeQueue: SourceQueueWithComplete[(Promise[Unit], Seq[T])] = Source
    .queue[(Promise[Unit], Seq[T])](baseDaoConfig.bufferSize, OverflowStrategy.dropNew)
    .batchWeighted[(Seq[Promise[Unit]], Seq[T])](baseDaoConfig.batchSize, _._2.size, tup => Vector(tup._1) -> tup._2) {
      case ((promises, rows), (newPromise, newRows)) => (promises :+ newPromise) -> (rows ++ newRows)
    }
    .mapAsync(baseDaoConfig.parallelism) {
      case (promises, rows) =>
        writeJournalRows(rows).map(unit => promises.foreach(_.success(unit))).recover {
          case t => promises.foreach(_.failure(t))
        }
    }
    .toMat(Sink.ignore)(Keep.left)
    .run()

  def writeJournalRows(xs: Seq[T]): Future[Unit]

  def queueWriteJournalRows(xs: Seq[T]): Future[Unit] = {
    val promise = Promise[Unit]()
    writeQueue.offer(promise -> xs).flatMap {
      case QueueOfferResult.Enqueued =>
        promise.future
      case QueueOfferResult.Failure(t) =>
        Future.failed(new Exception("Failed to write journal row batch", t))
      case QueueOfferResult.Dropped =>
        Future.failed(new Exception(
          s"Failed to enqueue journal row batch write, the queue buffer was full (${baseDaoConfig.bufferSize} elements) please check the jdbc-journal.bufferSize setting"))
      case QueueOfferResult.QueueClosed =>
        Future.failed(new Exception("Failed to enqueue journal row batch write, the queue was closed"))
    }
  }

}

/**
 * The DefaultJournalDao contains all the knowledge to persist and load serialized journal entries
 */
trait BaseByteArrayJournalDao
    extends JournalDaoWithUpdates
    with BaseJournalDaoWithReadMessages
    with BaseDao[JournalRow] {
  val db: Database
  val profile: JdbcProfile
  val queries: JournalQueries
  val journalConfig: JournalConfig
  val baseDaoConfig: BaseDaoConfig = journalConfig.daoConfig
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
      "Logical deletion of events is deprecated and will be removed in akka-persistence-jdbc in a later version " +
      "To disable it in this current version you must set the property 'akka-persistence-jdbc.logicalDeletion.enable' to false.")
  }

  def writeJournalRows(xs: Seq[JournalRow]): Future[Unit] = {
    // Write atomically without auto-commit
    db.run(queries.writeJournalRows(xs).transactionally).map(_ => ())
  }

  /**
   * @see [[akka.persistence.journal.AsyncWriteJournal.asyncWriteMessages(messages)]]
   */
  def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = {
    val serializedTries: Seq[Try[Seq[JournalRow]]] = serializer.serialize(messages)

    // If serialization fails for some AtomicWrites, the other AtomicWrites may still be written
    val rowsToWrite: Seq[JournalRow] = for {
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
      val actions: DBIOAction[Unit, NoStream, Effect.Write with Effect.Read] = for {
        _ <- queries.markJournalMessagesAsDeleted(persistenceId, maxSequenceNr)
        highestMarkedSequenceNr <- highestMarkedSequenceNr(persistenceId)
        _ <- queries.delete(persistenceId, highestMarkedSequenceNr.getOrElse(0L) - 1)
      } yield ()

      db.run(actions.transactionally)
    }

  def update(persistenceId: String, sequenceNr: Long, payload: AnyRef): Future[Done] = {
    val write = PersistentRepr(payload, sequenceNr, persistenceId)
    val serializedRow = serializer.serialize(write) match {
      case Success(t) => t
      case Failure(ex) =>
        throw new IllegalArgumentException(
          s"Failed to serialize ${write.getClass} for update of [$persistenceId] @ [$sequenceNr]")
    }
    db.run(queries.update(persistenceId, sequenceNr, serializedRow.message).map(_ => Done))
  }

  private def highestMarkedSequenceNr(persistenceId: String) =
    queries.highestMarkedSequenceNrForPersistenceId(persistenceId).result

  override def highestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] =
    for {
      maybeHighestSeqNo <- db.run(queries.highestSequenceNrForPersistenceId(persistenceId).result)
    } yield maybeHighestSeqNo.getOrElse(0L)

  override def messages(
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long,
      max: Long): Source[Try[(PersistentRepr, Long)], NotUsed] =
    Source
      .fromPublisher(db.stream(queries.messagesQuery(persistenceId, fromSequenceNr, toSequenceNr, max).result))
      .via(serializer.deserializeFlow)
      .map {
        case Success((repr, _, ordering)) => Success(repr -> ordering)
        case Failure(e)                   => Failure(e)
      }

}