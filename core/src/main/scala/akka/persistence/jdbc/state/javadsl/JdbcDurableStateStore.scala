/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.state.javadsl

import java.util.Optional
import java.util.concurrent.CompletionStage
import scala.compat.java8.FutureConverters.*
import scala.concurrent.ExecutionContext
import akka.annotation.ApiMayChange
import slick.jdbc.JdbcProfile
import akka.{Done, NotUsed}
import akka.persistence.state.javadsl.{DurableStateUpdateStore, GetObjectResult}
import akka.persistence.jdbc.state.DurableStateQueries
import akka.persistence.jdbc.config.DurableStateTableConfiguration
import akka.persistence.jdbc.state.scaladsl.JdbcDurableStateStore as ScalaJdbcDurableStateStore
import akka.persistence.query.{DurableStateChange, Offset}
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
    toJava(
      scalaStore
        .getObject(persistenceId)
        .map(x => GetObjectResult(Optional.ofNullable(x.value.getOrElse(null.asInstanceOf[A])), x.revision)))

  def upsertObject(persistenceId: String, revision: Long, value: A, tag: String): CompletionStage[Done] =
    toJava(scalaStore.upsertObject(persistenceId, revision, value, tag))

  @deprecated(message = "Use the deleteObject overload with revision instead.", since = "1.0.0")
  override def deleteObject(persistenceId: String): CompletionStage[Done] =
    deleteObject(persistenceId, revision = 0)

  @nowarn("msg=deprecated")
  override def deleteObject(persistenceId: String, revision: Long): CompletionStage[Done] =
    toJava(scalaStore.deleteObject(persistenceId))

  def currentChanges(tag: String, offset: Offset): Source[DurableStateChange[A], NotUsed] =
    scalaStore.currentChanges(tag, offset).asJava

  def changes(tag: String, offset: Offset): Source[DurableStateChange[A], NotUsed] =
    scalaStore.changes(tag, offset).asJava
}
