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

  val pluginConf: Config = ConfigFactory.parseString(s"""
    akka.loglevel = INFO
    akka.persistence.state.plugin = "akka.persistence.state.jdbc"
    akka.persistence.state.jdbc {
      class = "akka.persistence.jdbc.state.JdbcDurableStateStoreProvider"
    }
  """)

  implicit lazy val system: ExtendedActorSystem =
    ActorSystem("test", config.withFallback(pluginConf)).asInstanceOf[ExtendedActorSystem]

  "A durable state store plugin" must {
    "instantiate a JdbcDurableDataStore successfully" in {
      val store = DurableStateStoreRegistry
        .get(system)
        .durableStateStoreFor[JdbcDurableStateStore[String]]("akka.persistence.state.jdbc")

      store shouldBe a[JdbcDurableStateStore[_]]
      store.profile shouldBe profile
    }
  }

  override def afterAll(): Unit = {
    system.terminate().futureValue
  }
}

class H2DurableStateStorePluginSpec
    extends DurableStateStorePluginSpec(ConfigFactory.load("h2-application.conf"), H2Profile)
