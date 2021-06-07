/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.state.scaladsl

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.NotUsed
import akka.actor.ExtendedActorSystem
import akka.pattern.ask
import akka.persistence.jdbc.config.DurableStateTableConfiguration
import akka.persistence.query.DurableStateChange
import akka.persistence.query.Offset
import akka.persistence.state.scaladsl.GetObjectResult
import akka.stream.scaladsl.Source
import akka.testkit.TestProbe
import akka.util.Timeout
import slick.jdbc.{ JdbcBackend, JdbcProfile }
import akka.serialization.Serialization

object TestProbeDurableStateStoreQuery {
  case class OffsetSequence(offset: Long, limit: Long)
}

class TestProbeDurableStateStoreQuery(
    val probe: TestProbe,
    db: JdbcBackend#Database,
    profile: JdbcProfile,
    durableStateConfig: DurableStateTableConfiguration,
    serialization: Serialization)(override implicit val system: ExtendedActorSystem)
    extends JdbcDurableStateStore[String](db, profile, durableStateConfig, serialization)(system) {

  implicit val askTimeout = Timeout(100.millis)

  override def getObject(persistenceId: String): Future[GetObjectResult[String]] = ???
  override def currentChanges(tag: String, offset: Offset): Source[DurableStateChange[String], NotUsed] = ???

  override def changes(tag: String, offset: Offset): Source[DurableStateChange[String], NotUsed] = ???

  override def stateStoreSequence(offset: Long, limit: Long): Source[Long, NotUsed] = {
    val f = probe.ref
      .ask(TestProbeDurableStateStoreQuery.OffsetSequence(offset, limit))
      .mapTo[scala.collection.immutable.Seq[Long]]

    Source.future(f).mapConcat(identity)
  }

  override def maxStateStoreOffset(): Future[Long] = Future.successful(0)
}
