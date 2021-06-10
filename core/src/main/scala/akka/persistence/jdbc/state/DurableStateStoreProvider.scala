/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.state

import slick.jdbc.{ JdbcBackend, JdbcProfile }
import akka.persistence.state.DurableStateStoreProvider
import akka.persistence.jdbc.config.DurableStateTableConfiguration
import akka.persistence.state.scaladsl.DurableStateStore
import akka.persistence.state.javadsl.{ DurableStateStore => JDurableStateStore }
import akka.serialization.Serialization

import scala.concurrent.ExecutionContext

class JdbcDurableStateStoreProvider[A](
    db: JdbcBackend#Database,
    profile: JdbcProfile,
    durableStateConfig: DurableStateTableConfiguration,
    serialization: Serialization)(implicit ec: ExecutionContext)
    extends DurableStateStoreProvider {

  override def scaladslDurableStateStore(): DurableStateStore[Any] =
    new scaladsl.JdbcDurableStateStore[Any](db, profile, durableStateConfig, serialization)

  override def javadslDurableStateStore(): JDurableStateStore[AnyRef] =
    new javadsl.JdbcDurableStateStore[AnyRef](
      profile,
      durableStateConfig,
      new scaladsl.JdbcDurableStateStore[AnyRef](db, profile, durableStateConfig, serialization))
}
