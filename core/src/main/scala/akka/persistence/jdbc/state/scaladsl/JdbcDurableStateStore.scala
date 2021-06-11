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

class JdbcDurableStateStore[A](
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
          GetObjectResult(None, 0)
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
        .flatMap(r => db.run(queries._updateDurableState(r)))
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
}
