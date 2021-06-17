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
import akka.persistence.jdbc.state.DurableStateTables
import akka.dispatch.ExecutionContexts
import akka.persistence.query.scaladsl.DurableStateStoreQuery
import akka.persistence.jdbc.journal.dao.FlowControl
import akka.stream.scaladsl.{ Sink, Source }
import akka.persistence.query.{ Offset, Sequence => APSequence, NoOffset, DurableStateChange }
import akka.stream.{ Materializer, SystemMaterializer }

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
    db.run(queries._selectByPersistenceId(persistenceId).result).map { rows =>
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

    if (seqNr == 1) {
      // this insert will fail if any record exists in the table with the same persistence id
      // in case concurrent users try to insert, one will fail because the transactions will be serializable
      // and we have a primary key on persistence_id - hence fail with integrity constraints violation
      // on primary key constraints
      Future
        .fromTry(row)
        .flatMap(r => db.run(queries._insertDurableState(r)))
        .map(_ => Done)(ExecutionContexts.parasitic)
    } else {
      // if seqNr > 1 we always try update and if that fails (returns 0 affected rows) we throw
      Future
        .fromTry(row)
        .flatMap(r => db.run(updateDurableState(r)))
        .map { rowsAffected =>
          if (rowsAffected == 0)
            throw new IllegalStateException(
              s"Incorrect sequence number [$seqNr] provided: It has to be 1 more than the value existing in the database for persistenceId [$persistenceId]")
          else Done
        }(ExecutionContexts.parasitic)
    }
  }

  def deleteObject(persistenceId: String): Future[Done] =
    db.run(queries._delete(persistenceId).map(_ => Done))

  def currentChanges(tag: String, offset: Offset): Source[DurableStateChange[A], NotUsed] = (offset match {
    case NoOffset      => makeSourceFromStateQuery(tag, None)
    case APSequence(l) => makeSourceFromStateQuery(tag, Some(l))
    case _             => ??? // should not reach here
  })

  def changes(tag: String, offset: Offset): Source[DurableStateChange[A], NotUsed] = {

    // a collection to store the start offsets for every iteration 
    val lastStartOffsets = collection.mutable.ListBuffer.empty[Long]
    val stopAfterEmptyFetchIterations = durableStateConfig.stopAfterEmptyFetchIterations 
    Source
      .unfoldAsync[(Offset, FlowControl), Seq[DurableStateChange[A]]]((offset, Continue)) { case (from, control) =>
        def retrieveNextBatch() = {
          // we will Stop after this many empty fetches
          // may be this should come from config
          /* get all records from the specified offset */
          currentChanges(tag, from).runWith(Sink.seq).map { chgs =>
            val offsets: Seq[Long] = chgs.map { chg =>
              chg.offset match {
                case NoOffset      => 0
                case APSequence(l) => l
                case _             => 0 // should not reach here
              }
            }
            val nextControl = {
              // check if we have reached `stopAfterEmptyFetchIterations` empty fetch cycles
              // Stop if we have else ContinueDelayed
              if (stopAfterEmptyFetchIterations > 0) {
                val tailOffsetsUnchanged = lastStartOffsets.slice(lastStartOffsets.size - stopAfterEmptyFetchIterations, lastStartOffsets.size)
                if (tailOffsetsUnchanged.size == stopAfterEmptyFetchIterations && tailOffsetsUnchanged.forall(_ == tailOffsetsUnchanged.head)) Stop
                else ContinueDelayed
              } else ContinueDelayed
            } 

            val nextStartOffset: Long =
              if (offsets.nonEmpty) {
                lastStartOffsets += offsets.max
                offsets.max
              } else {
                val l = lastStartOffsets.last
                lastStartOffsets += lastStartOffsets.last
                l
              }

            Some(((Offset.sequence(nextStartOffset), nextControl), chgs))
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

  private def makeSourceFromStateQuery(tag: String, offset: Option[Long]): Source[DurableStateChange[A], NotUsed] = {
    Source
      .fromPublisher(db.stream(queries._selectByTag(Some(tag), offset).result))
      .mapAsync(1) { row =>
        Future.fromTry(toDurableStateChange(row)) 
      }
  }

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
      s <- _getSequenceName()
      u <- _updateDurableState(row, s.head)
    } yield u
  }
}
