/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2025 Lightbend Inc. <https://akka.io>
 */

package akka.persistence.jdbc.state.javadsl

import java.util.Optional
import java.util.concurrent.CompletionStage
import scala.jdk.FutureConverters._
import scala.concurrent.ExecutionContext
import akka.annotation.ApiMayChange
import slick.jdbc.JdbcProfile
import akka.{ Done, NotUsed }
import akka.persistence.state.javadsl.{ DurableStateUpdateStore, GetObjectResult }
import akka.persistence.jdbc.state.DurableStateQueries
import akka.persistence.jdbc.config.DurableStateTableConfiguration
import akka.persistence.jdbc.state.scaladsl.{ JdbcDurableStateStore => ScalaJdbcDurableStateStore }
import akka.persistence.query.{ DurableStateChange, Offset }
import akka.persistence.query.javadsl.DurableStateStoreQuery
import akka.stream.javadsl.Source

import scala.annotation.nowarn

object JdbcDurableStateStore {
  val Identifier = ScalaJdbcDurableStateStore.Identifier
}

/**
 * API may change
 */
@ApiMayChange
class JdbcDurableStateStore[A](
    profile: JdbcProfile,
    durableStateConfig: DurableStateTableConfiguration,
    scalaStore: akka.persistence.jdbc.state.scaladsl.JdbcDurableStateStore[A])(implicit ec: ExecutionContext)
    extends DurableStateUpdateStore[A]
    with DurableStateStoreQuery[A] {

  val queries = new DurableStateQueries(profile, durableStateConfig)

  def getObject(persistenceId: String): CompletionStage[GetObjectResult[A]] =
    scalaStore
      .getObject(persistenceId)
      .map(x => GetObjectResult(Optional.ofNullable(x.value.getOrElse(null.asInstanceOf[A])), x.revision))
      .asJava

  def upsertObject(persistenceId: String, revision: Long, value: A, tag: String): CompletionStage[Done] =
    scalaStore.upsertObject(persistenceId, revision, value, tag).asJava

  @deprecated(message = "Use the deleteObject overload with revision instead.", since = "1.0.0")
  override def deleteObject(persistenceId: String): CompletionStage[Done] =
    deleteObject(persistenceId, revision = 0)

  @nowarn("msg=deprecated")
  override def deleteObject(persistenceId: String, revision: Long): CompletionStage[Done] =
    scalaStore.deleteObject(persistenceId).asJava

  def currentChanges(tag: String, offset: Offset): Source[DurableStateChange[A], NotUsed] =
    scalaStore.currentChanges(tag, offset).asJava

  def changes(tag: String, offset: Offset): Source[DurableStateChange[A], NotUsed] =
    scalaStore.changes(tag, offset).asJava
}
