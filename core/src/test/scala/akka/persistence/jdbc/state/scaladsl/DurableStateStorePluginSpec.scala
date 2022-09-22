/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.state.scaladsl

import com.typesafe.config.{ Config, ConfigFactory }
import akka.actor._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import akka.persistence.jdbc.state.scaladsl.JdbcDurableStateStore
import akka.persistence.state.DurableStateStoreRegistry
import slick.jdbc.{ H2Profile, JdbcProfile }

abstract class DurableStateStorePluginSpec(config: Config, profile: JdbcProfile)
    extends AnyWordSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  implicit lazy val system: ExtendedActorSystem =
    ActorSystem("test", config).asInstanceOf[ExtendedActorSystem]

  "A durable state store plugin" must {
    "instantiate a JdbcDurableDataStore successfully" in {
      val store = DurableStateStoreRegistry
        .get(system)
        .durableStateStoreFor[JdbcDurableStateStore[String]](JdbcDurableStateStore.Identifier)

      store shouldBe a[JdbcDurableStateStore[_]]
      store.system.settings.config shouldBe system.settings.config
      store.profile shouldBe profile
    }
  }

  override def afterAll(): Unit = {
    system.terminate().futureValue
  }
}

class H2DurableStateStorePluginSpec
    extends DurableStateStorePluginSpec(ConfigFactory.load("h2-application.conf"), H2Profile)
