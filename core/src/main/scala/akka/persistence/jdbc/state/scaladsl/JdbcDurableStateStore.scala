package akka.persistence.jdbc.state.scaladsl

import scala.util.Try
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import slick.jdbc.{ JdbcBackend, JdbcProfile }
import akka.{ Done, NotUsed }
import akka.actor.ActorSystem
import akka.persistence.state.scaladsl.{ DurableStateUpdateStore, GetObjectResult }
import akka.serialization.Serialization
import akka.persistence.jdbc.state.{ AkkaSerialization, DurableStateQueries }
import akka.persistence.jdbc.config.DurableStateTableConfiguration
import akka.persistence.jdbc.state.{ DurableStateTables, OffsetSyntax }
import OffsetSyntax._
import akka.persistence.query.scaladsl.DurableStateStoreQuery
import akka.persistence.jdbc.journal.dao.FlowControl
import akka.stream.scaladsl.{ Sink, Source }
import akka.persistence.query.{ DurableStateChange, Offset }
import akka.stream.{ Materializer, SystemMaterializer }

case class MaxOffset(value: Long) extends AnyVal
class JdbcDurableStateStore[A](
    db: JdbcBackend#Database,
    profile: JdbcProfile,
    durableStateConfig: DurableStateTableConfiguration,
    serialization: Serialization)(implicit val system: ActorSystem)
    extends DurableStateUpdateStore[A]
    with DurableStateStoreQuery[A] {
  import FlowControl._
  import profile.api._

  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = SystemMaterializer(system).materializer

  val queries = new DurableStateQueries(profile, durableStateConfig)

  def getObject(persistenceId: String): Future[GetObjectResult[A]] = {
    db.run(queries.selectFromDbByPersistenceId(persistenceId).result).map { rows =>
      rows.headOption match {
        case Some(row) =>
          GetObjectResult(AkkaSerialization.fromRow(serialization)(row).toOption.asInstanceOf[Option[A]], row.seqNumber)

        case None =>
          GetObjectResult(None, 0)
      }
    }
  }

  def upsertObject(persistenceId: String, seqNr: Long, value: A, tag: String): Future[Done] = {
    require(seqNr > 0)
    val row =
      AkkaSerialization.serialize(serialization, value).map { serialized =>
        DurableStateTables.DurableStateRow(
          0, // insert 0 for autoinc columns
          persistenceId,
          seqNr,
          serialized.payload,
          Option(tag).filter(_.trim.nonEmpty),
          serialized.serId,
          Option(serialized.serManifest).filter(_.trim.nonEmpty),
          System.currentTimeMillis)
      }

    Future
      .fromTry(row)
      .flatMap { r =>
        val action = if (seqNr == 1) insertDurableState(r) else updateDurableState(r)
        db.run(action)
      }
      .map { rowsAffected =>
        if (rowsAffected == 0)
          throw new IllegalStateException(
            s"Incorrect sequence number [$seqNr] provided: It has to be 1 more than the value existing in the database for persistenceId [$persistenceId]")
        else Done
      }
  }

  def deleteObject(persistenceId: String): Future[Done] =
    db.run(queries.deleteFromDb(persistenceId).map(_ => Done))

  def currentChanges(tag: String, offset: Offset): Source[DurableStateChange[A], NotUsed] = {
    Source
      .futureSource(maxJournalSequence().map { maxOrderingInDb =>
        changesByTag(tag, offset.value, terminateAfterOffset = Some(maxOrderingInDb))
      })
      .mapMaterializedValue(_ => NotUsed)
  }

  def changes(tag: String, offset: Offset): Source[DurableStateChange[A], NotUsed] =
    changesByTag(tag, offset.value, terminateAfterOffset = None)

  private def currentChangesByTag(
      tag: String,
      from: Long,
      batchSize: Long,
      queryUntil: MaxOffset): Source[DurableStateChange[A], NotUsed] = {
    if (queryUntil.value < from.value) Source.empty
    else changesByTagFromDb(tag, from, queryUntil.value, batchSize).mapAsync(1)(Future.fromTry)
  }

  private def changesByTagFromDb(
      tag: String,
      offset: Long,
      maxOffset: Long,
      batchSize: Long): Source[Try[DurableStateChange[A]], NotUsed] = {
    Source
      .fromPublisher(db.stream(queries.changesByTag((tag, offset, maxOffset, batchSize)).result))
      .map(toDurableStateChange)
  }

  private def changesByTag(
      tag: String,
      offset: Long,
      terminateAfterOffset: Option[Long]): Source[DurableStateChange[A], NotUsed] = {
    val batchSize = durableStateConfig.batchSize
    val startingOffsets = collection.mutable.ListBuffer.empty[Long]
    val stopAfterEmptyFetchIterations = durableStateConfig.stopAfterEmptyFetchIterations
    Source
      .unfoldAsync[(Long, FlowControl), Seq[DurableStateChange[A]]]((offset, Continue)) { case (from, control) =>
        def retrieveNextBatch() = {
          for {
            queryUntil <- maxJournalSequence()
            xs <- currentChangesByTag(tag, from, batchSize, MaxOffset(queryUntil)).runWith(Sink.seq)
          } yield {
            val hasMoreEvents = xs.size == batchSize
            val nextControl: FlowControl =
              terminateAfterOffset match {
                // we may stop if target is behind queryUntil and we don't have more events to fetch
                case Some(target) if !hasMoreEvents && target <= queryUntil => Stop

                // We may also stop if we have found an event with an offset >= target
                case Some(target) if xs.exists(_.offset.value >= target) => Stop

                // otherwise, disregarding if Some or None, we must decide how to continue
                case _ =>
                  if (
                    stopAfterEmptyFetchIterations > 0 && equalLastNElements(
                      startingOffsets.toList,
                      stopAfterEmptyFetchIterations)
                  ) Stop
                  else if (hasMoreEvents) Continue
                  else ContinueDelayed
              }
            val nextStartingOffset = if (xs.isEmpty) {
              math.max(from.value, queryUntil)
            } else {
              // Continue querying from the largest offset
              xs.map(_.offset.value).max
            }
            startingOffsets += nextStartingOffset
            Some(((nextStartingOffset, nextControl), xs))
          }
        }

        control match {
          case Stop     => Future.successful(None)
          case Continue => retrieveNextBatch()
          case ContinueDelayed =>
            akka.pattern.after(durableStateConfig.refreshInterval, system.scheduler)(retrieveNextBatch())
        }
      }
      .mapConcat(identity)
  }

  // checks if the last n elements of a list are equal
  private def equalLastNElements(coll: List[Long], n: Int): Boolean = {
    if (coll.isEmpty) false
    else if (coll.size < n) false
    else {
      val res = coll.slice(coll.length - n, coll.length)
      res.forall(_ == res.head)
    }
  }

  private def maxJournalSequence(): Future[Long] =
    db.run(queries.maxOffsetQuery.result)

  private def toDurableStateChange(row: DurableStateTables.DurableStateRow): Try[DurableStateChange[A]] = {
    AkkaSerialization
      .fromRow(serialization)(row)
      .map(payload =>
        new DurableStateChange(
          row.persistenceId,
          row.seqNumber,
          payload.asInstanceOf[A],
          Offset.sequence(row.globalOffset),
          row.stateTimestamp))
  }

  private def updateDurableState(row: DurableStateTables.DurableStateRow) = {
    import queries._

    for {
      s <- getSequenceNextValueExpr()
      u <- updateDbWithDurableState(row, s.head)
    } yield u
  }

  private def insertDurableState(row: DurableStateTables.DurableStateRow) = {
    import queries._

    for {
      s <- getSequenceNextValueExpr()
      u <- insertDbWithDurableState(row, s.head)
    } yield u
  }
}
