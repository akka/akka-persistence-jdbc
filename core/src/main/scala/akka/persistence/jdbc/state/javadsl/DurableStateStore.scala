package akka.persistence.jdbc.state.javadsl

import java.util.Optional
import java.util.concurrent.CompletionStage
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future }
import slick.jdbc.{ JdbcBackend, JdbcProfile }
import akka.Done
import akka.persistence.state.javadsl.{ DurableStateUpdateStore, GetObjectResult }
import akka.serialization.Serialization
import akka.persistence.jdbc.state.{ AkkaSerialization, DurableStateQueries }
import akka.persistence.jdbc.config.DurableStateTableConfiguration
import akka.persistence.jdbc.state.DurableStateTables
import akka.dispatch.ExecutionContexts

class DurableStateStore[A](
    db: JdbcBackend#Database,
    profile: JdbcProfile,
    durableStateConfigConfig: DurableStateTableConfiguration,
    serialization: Serialization)(implicit ec: ExecutionContext)
    extends DurableStateUpdateStore[A] {
  import profile.api._

  val queries = new DurableStateQueries(profile, durableStateConfigConfig)

  def getObject(persistenceId: String): CompletionStage[GetObjectResult[A]] =
    toJava(db.run(queries._selectByPersistenceId(persistenceId).result).map { rows =>
      rows.headOption match {
        case Some(row) =>
          GetObjectResult(
            Optional
              .ofNullable(AkkaSerialization.fromRow(serialization)(row).toOption.getOrElse(null))
              .asInstanceOf[Optional[A]],
            row.seqNumber)

        case None =>
          throw new Exception(s"State object creation failed during fetch from store: persistenceId $persistenceId")
      }
    })

  def upsertObject(persistenceId: String, seqNr: Long, value: A, tag: String): CompletionStage[Done] = {
    require(seqNr > 0)
    val row =
      AkkaSerialization.serialize(serialization, value).map { serialized =>
        DurableStateTables
          .DurableStateRow(persistenceId, serialized.payload, seqNr, serialized.serId, serialized.serManifest)
      }

    toJava(
      Future.fromTry(row).map(queries._upsertDurableState).flatMap(db.run).map(_ => Done)(ExecutionContexts.parasitic))
  }

  def deleteObject(persistenceId: String): CompletionStage[Done] =
    toJava(db.run(queries._delete(persistenceId)).map(_ => Done)(ExecutionContexts.parasitic))
}
