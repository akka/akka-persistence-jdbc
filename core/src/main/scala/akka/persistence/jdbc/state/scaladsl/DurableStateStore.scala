package akka.persistence.jdbc.state.scaladsl

import scala.concurrent.{ ExecutionContext, Future }
import slick.jdbc.{ JdbcBackend, JdbcProfile }
import akka.Done
import akka.persistence.state.scaladsl.{ DurableStateUpdateStore, GetObjectResult }
import akka.serialization.Serialization
import akka.persistence.jdbc.state.{ AkkaSerialization, DurableStateQueries }
import akka.persistence.jdbc.config.DurableStateTableConfiguration
import akka.persistence.jdbc.state.DurableStateTables
import akka.dispatch.ExecutionContexts

class DurableStateStore[A](
    db: JdbcBackend#Database,
    profile: JdbcProfile,
    durableStateConfig: DurableStateTableConfiguration,
    serialization: Serialization)(implicit ec: ExecutionContext)
    extends DurableStateUpdateStore[A] {
  import profile.api._

  val queries = new DurableStateQueries(profile, durableStateConfig)

  def getObject(persistenceId: String): Future[GetObjectResult[A]] =
    db.run(queries._selectByPersistenceId(persistenceId).result).map { rows =>
      rows.headOption match {
        case Some(row) =>
          GetObjectResult(AkkaSerialization.fromRow(serialization)(row).toOption.asInstanceOf[Option[A]], row.seqNumber)

        case None =>
          throw new Exception(s"State object creation failed during fetch from store: persistenceId $persistenceId")
      }
    }

  def upsertObject(persistenceId: String, seqNr: Long, value: A, tag: String): Future[Done] = {
    require(seqNr > 0)
    val row =
      AkkaSerialization.serialize(serialization, value).map { serialized =>
        println(s"serialized: ${serialized.serId}, ${serialized.serManifest}")
        DurableStateTables.DurableStateRow(
          persistenceId,
          serialized.payload,
          seqNr,
          serialized.serId,
          (if (serialized.serManifest.isEmpty()) None else Some(serialized.serManifest)))
      }

    Future.fromTry(row).map(queries._upsertDurableState).flatMap(db.run).map(_ => Done)(ExecutionContexts.parasitic)
  }

  def deleteObject(persistenceId: String): Future[Done] =
    db.run(queries._delete(persistenceId)).map(_ => Done)(ExecutionContexts.parasitic)
}
