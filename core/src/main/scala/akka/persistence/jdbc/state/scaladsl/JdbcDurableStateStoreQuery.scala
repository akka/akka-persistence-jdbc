package akka.persistence.jdbc.state.scaladsl

import scala.concurrent.Future
import scala.util.Try
import akka.NotUsed
import akka.persistence.query.scaladsl.DurableStateStoreQuery
import akka.persistence.state.scaladsl.GetObjectResult
import akka.persistence.query.{ Offset, Sequence => APSequence, NoOffset }
import akka.persistence.query.DurableStateChange
import akka.persistence.jdbc.config.DurableStateTableConfiguration
import akka.persistence.jdbc.state.{ AkkaSerialization, DurableStateQueries, DurableStateTables }
import akka.serialization.Serialization
import akka.stream.scaladsl.Source
import slick.jdbc.{ JdbcBackend, JdbcProfile }

class JdbcDurableStateStoreQuery[A](
    stateStore: JdbcDurableStateStore[A],
    db: JdbcBackend#Database,
    profile: JdbcProfile,
    durableStateConfig: DurableStateTableConfiguration,
    serialization: Serialization)
    extends DurableStateStoreQuery[A] {
  import profile.api._

  val queries = new DurableStateQueries(profile, durableStateConfig)

  def currentChanges(tag: String, offset: Offset): Source[DurableStateChange[A], NotUsed] = offset match {
    case NoOffset      => makeSourceFromStateQuery(tag, None)
    case APSequence(l) => makeSourceFromStateQuery(tag, Some(l))
    case _             => ??? // should not reach here
  }

  def changes(tag: String, offset: Offset): Source[DurableStateChange[A], NotUsed] = ???

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
