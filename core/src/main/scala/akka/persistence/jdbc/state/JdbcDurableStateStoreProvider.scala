/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.state

import scala.concurrent.ExecutionContext
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcBackend._
import akka.actor.ExtendedActorSystem
import akka.persistence.jdbc.config.DurableStateTableConfiguration
import akka.persistence.state.scaladsl.DurableStateStore
import akka.persistence.state.javadsl.{ DurableStateStore => JDurableStateStore }
import akka.persistence.state.DurableStateStoreProvider
import akka.persistence.jdbc.db.{ SlickDatabase, SlickExtension }
import akka.serialization.SerializationExtension
import akka.stream.{ Materializer, SystemMaterializer }
import com.typesafe.config.Config

class JdbcDurableStateStoreProvider[A](system: ExtendedActorSystem, cfg: Config, cfgPath: String)
    extends DurableStateStoreProvider {

  implicit val ec: ExecutionContext = system.dispatcher
  implicit val mat: Materializer = SystemMaterializer(system).materializer

  val config = system.settings.config

  val slickDb: SlickDatabase =
    SlickExtension(system).database(config.getConfig(cfgPath))
  def db: Database = slickDb.database

  lazy val durableStateConfig = new DurableStateTableConfiguration(config.getConfig(cfgPath))
  lazy val serialization = SerializationExtension(system)
  val profile: JdbcProfile = slickDb.profile

  private lazy val _scaladslDurableStateStore: DurableStateStore[Any] =
    new scaladsl.JdbcDurableStateStore[Any](cfgPath, db, profile, durableStateConfig, serialization)(system)

  override def scaladslDurableStateStore(): DurableStateStore[Any] =
    _scaladslDurableStateStore

  override def javadslDurableStateStore(): JDurableStateStore[AnyRef] =
    new javadsl.JdbcDurableStateStore[AnyRef](
      profile,
      durableStateConfig,
      _scaladslDurableStateStore.asInstanceOf[scaladsl.JdbcDurableStateStore[AnyRef]])
}
