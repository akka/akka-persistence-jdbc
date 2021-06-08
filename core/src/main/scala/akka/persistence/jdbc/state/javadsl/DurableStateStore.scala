package akka.persistence.jdbc.state.javadsl

import java.util.Optional
import java.util.concurrent.CompletionStage
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext
import slick.jdbc.JdbcProfile
import akka.Done
import akka.persistence.state.javadsl.{ DurableStateUpdateStore, GetObjectResult }
import akka.persistence.jdbc.state.DurableStateQueries
import akka.persistence.jdbc.config.DurableStateTableConfiguration

class DurableStateStore[A](
    profile: JdbcProfile,
    durableStateConfigConfig: DurableStateTableConfiguration,
    scalaStore: akka.persistence.jdbc.state.scaladsl.DurableStateStore[A])(implicit ec: ExecutionContext)
    extends DurableStateUpdateStore[A] {

  val queries = new DurableStateQueries(profile, durableStateConfigConfig)

  def getObject(persistenceId: String): CompletionStage[GetObjectResult[A]] =
    toJava(
      scalaStore
        .getObject(persistenceId)
        .map(x => GetObjectResult(Optional.ofNullable(x.value.getOrElse(null.asInstanceOf[A])), x.seqNr)))

  def upsertObject(persistenceId: String, seqNr: Long, value: A, tag: String): CompletionStage[Done] =
    toJava(scalaStore.upsertObject(persistenceId, seqNr, value, tag))

  def deleteObject(persistenceId: String): CompletionStage[Done] =
    toJava(scalaStore.deleteObject(persistenceId))
}
