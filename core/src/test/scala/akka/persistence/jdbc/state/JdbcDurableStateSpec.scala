package akka.persistence.jdbc.state

import com.typesafe.config.{ Config, ConfigFactory }

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures

import akka.actor._
import akka.testkit._
import akka.persistence.jdbc.db.SlickDatabase
import akka.persistence.jdbc.config._
import akka.persistence.jdbc.state.scaladsl.DurableStateStore
import akka.persistence.jdbc.testkit.internal.{ H2, SchemaType }
import akka.persistence.jdbc.util.DropCreate
import akka.serialization.SerializationExtension

abstract class JdbcDurableStateSpec(conf: Config, schemaType: SchemaType)
    extends TestKitBase
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers
    with ScalaFutures
    with DropCreate {

  implicit lazy val ec = system.dispatcher
  val config = conf

  lazy val cfg = system.settings.config
    .getConfig("jdbc-durable-state-store")
    .withFallback(config.getConfig("jdbc-durable-state-store"))

  lazy val db = SlickDatabase.database(cfg, new SlickConfiguration(cfg.getConfig("slick")), "slick.db")
  lazy val durableStateConfig = new DurableStateTableConfiguration(cfg)
  implicit lazy val system: ActorSystem = ActorSystem("JdbcDurableStateSpec")
  lazy val serialization = SerializationExtension(system)
  val stateStoreString: DurableStateStore[String]

  override def beforeAll(): Unit = {
    dropAndCreate(schemaType)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    db.close()
  }

  "A durable state store" must {
    "not load a state given an invalid persistenceId" in {
      whenReady {
        stateStoreString.getObject("InvalidPersistenceId").failed
      } { e =>
        e shouldBe an[java.lang.Exception]
      }
    }
    "add a valid state successfully" in {
      whenReady {
        // upsert
        stateStoreString.upsertObject("p123", 1, "a valid string", "t123")
      } { v =>
        v shouldBe akka.Done
        whenReady {
          // first time : value should be inserted
          stateStoreString.getObject("p123")
        } { v =>
          v.value shouldBe Some("a valid string")
        }
        whenReady {
          // again upsert for the same persistenceId
          stateStoreString.upsertObject("p123", 2, "updated valid string", "t123")
        } { v =>
          v shouldBe akka.Done
          // value should be updated
          whenReady {
            stateStoreString.getObject("p123")
          } { v =>
            v.value shouldBe Some("updated valid string")
          }
        }
      }
    }
    "delete an exsiting state" in {
      whenReady {
        stateStoreString.deleteObject("p123")
      } { v =>
        v shouldBe akka.Done
        whenReady {
          stateStoreString.getObject("p123").failed
        } { e =>
          e shouldBe an[java.lang.Exception]
        }
      }
    }
    "handle composite operations" in {
      whenReady {
        for {
          _ <- stateStoreString.upsertObject("p234", 1, "valid string state", "tag-123")
          _ <- stateStoreString.getObject("p234")
          _ <- stateStoreString.upsertObject("p234", 1, "another valid string state", "tag-123")
          w <- stateStoreString.getObject("p234")
        } yield w
      } { v =>
        v.value shouldBe Some("another valid string state")
      }
    }
  }
}

class H2DurableStateSpec extends JdbcDurableStateSpec(ConfigFactory.load("h2-application.conf"), H2) {
  final val stateStoreString =
    new DurableStateStore[String](db, slick.jdbc.H2Profile, durableStateConfig, serialization)
}
