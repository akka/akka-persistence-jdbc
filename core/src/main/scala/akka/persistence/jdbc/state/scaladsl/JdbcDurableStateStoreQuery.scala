package akka.persistence.jdbc.state.scaladsl

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try
import akka.NotUsed
import akka.persistence.query.scaladsl.DurableStateStoreQuery
import akka.persistence.state.scaladsl.GetObjectResult
import akka.persistence.query.{ Offset, Sequence => APSequence, NoOffset }
import akka.persistence.query.DurableStateChange
import akka.persistence.jdbc.config.DurableStateTableConfiguration
import akka.persistence.jdbc.state.{ AkkaSerialization, DurableStateQueries, DurableStateTables }
import akka.persistence.jdbc.journal.dao.FlowControl
import akka.serialization.Serialization
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import slick.jdbc.{ JdbcBackend, JdbcProfile }

class JdbcDurableStateStoreQuery[A](
    stateStore: JdbcDurableStateStore[A],
    db: JdbcBackend#Database,
    profile: JdbcProfile,
    durableStateConfig: DurableStateTableConfiguration,
    serialization: Serialization)(implicit m: Materializer, e: ExecutionContext)
    extends DurableStateStoreQuery[A] {
  import FlowControl._
  import profile.api._

  val queries = new DurableStateQueries(profile, durableStateConfig)

  def currentChanges(tag: String, offset: Offset): Source[DurableStateChange[A], NotUsed] = offset match {
    case NoOffset      => makeSourceFromStateQuery(tag, None)
    case APSequence(l) => makeSourceFromStateQuery(tag, Some(l))
    case _             => ??? // should not reach here
  }

  def changes(tag: String, offset: Offset): Source[DurableStateChange[A], NotUsed] = {

    Source
      .unfoldAsync[(Offset, FlowControl), Seq[DurableStateChange[A]]]((offset, Continue)) { case (from, control) =>
        def retrieveNextBatch() = {
          /* get all records from the specified offset */
          currentChanges(tag, from).runWith(Sink.seq).map { chgs =>
            val nextStartOffset: Long = chgs.map { chg =>
              chg.offset match {
                case NoOffset      => 0
                case APSequence(l) => l
                case _             => 0 // should not reach here
              }
            }.max
            /* if retrieved set is empty, then wait else continue */
            val nextControl = if (chgs.isEmpty) ContinueDelayed else Continue
            Some(((Offset.sequence(nextStartOffset), nextControl), chgs))
          }
        }
        retrieveNextBatch()
      }
      .mapConcat(identity)
  }

  def getObject(persistenceId: String): Future[GetObjectResult[A]] = stateStore.getObject(persistenceId)

  private def makeSourceFromStateQuery(tag: String, offset: Option[Long]): Source[DurableStateChange[A], NotUsed] = {
    Source.fromPublisher(db.stream(queries._selectByTag(Some(tag), offset).result)).map { row =>
      toDurableStateChange(row).getOrElse(throw new IllegalStateException(s"Error fetching state information for $tag"))
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
          Offset.sequence(row.ordering),
          row.stateTimestamp))
  }
}
