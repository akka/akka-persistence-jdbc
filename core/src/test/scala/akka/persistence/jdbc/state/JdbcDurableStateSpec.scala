package akka.persistence.jdbc.state

import com.typesafe.config.{ Config, ConfigFactory }

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }
import org.scalatest.concurrent.ScalaFutures

import akka.actor._
import akka.persistence.PluginSpec
import akka.persistence.jdbc.db.SlickDatabase
import akka.persistence.jdbc.config._
import akka.persistence.jdbc.state.scaladsl.JdbcDurableStateStore
import akka.persistence.jdbc.testkit.internal.{ H2, SchemaType }
import akka.persistence.jdbc.util.DropCreate
import akka.serialization.SerializationExtension

abstract class JdbcDurableStateSpec(config: Config, schemaType: SchemaType)
    extends PluginSpec(config)
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers
    with ScalaFutures
    with DropCreate {

  implicit lazy val ec = system.dispatcher

  val customSerializers = ConfigFactory.parseString("""
      akka.actor {
        serializers {
          my-payload = "akka.persistence.jdbc.state.MyPayloadSerializer"
        }
        serialization-bindings {
          "akka.persistence.jdbc.state.MyPayload" = my-payload
        }
      }
    """)

  lazy val cfg = system.settings.config
    .getConfig("jdbc-durable-state-store")
    .withFallback(config.getConfig("jdbc-durable-state-store"))
    .withFallback(customSerializers.getConfig("akka.actor"))

  lazy val db = SlickDatabase.database(cfg, new SlickConfiguration(cfg.getConfig("slick")), "slick.db")
  lazy val durableStateConfig = new DurableStateTableConfiguration(cfg)
  implicit lazy val system: ActorSystem = ActorSystem("JdbcDurableStateSpec", config.withFallback(customSerializers))
  lazy val serialization = SerializationExtension(system)

  val stateStoreString: JdbcDurableStateStore[String]
  val stateStorePayload: JdbcDurableStateStore[MyPayload]

  override def beforeAll(): Unit = {
    dropAndCreate(schemaType)
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    dropAndCreate(schemaType)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    db.close()
  }

  "A durable state store" must {
    "not load a state given an invalid persistenceId" in {
      whenReady {
        stateStoreString.getObject("InvalidPersistenceId")
      } { v =>
        v.value shouldBe None
      }
    }
    "add a valid state successfully" in {
      whenReady {
        stateStoreString.upsertObject("p123", 1, "a valid string", "t123")
      } { v =>
        v shouldBe akka.Done
      }
    }
    "support composite upsert-fetch-repeat loop" in {
      whenReady {
        for {

          n <- stateStoreString.upsertObject("p234", 1, "a valid string", "t123")
          _ = n shouldBe akka.Done
          g <- stateStoreString.getObject("p234")
          _ = g.value shouldBe Some("a valid string")
          u <- stateStoreString.upsertObject("p234", 2, "updated valid string", "t123")
          _ = u shouldBe akka.Done
          h <- stateStoreString.getObject("p234")

        } yield h
      } { v =>
        v.value shouldBe Some("updated valid string")
      }
    }
    "fail inserting incorrect sequence number" in {
      whenReady {
        (for {

          n <- stateStoreString.upsertObject("p345", 1, "a valid string", "t123")
          _ = n shouldBe akka.Done
          g <- stateStoreString.getObject("p345")
          _ = g.value shouldBe Some("a valid string")
          u <- stateStoreString.upsertObject("p345", 1, "updated valid string", "t123")

        } yield u).failed
      } { e =>
        e shouldBe an[org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException]
      }
    }
    "fail inserting an already existing sequence number" in {
      whenReady {
        stateStoreString.upsertObject("p234", 2, "another valid string", "t123").failed
      } { e =>
        e shouldBe an[IllegalStateException]
      }
    }
    "delete an existing state" in {
      whenReady {
        stateStoreString.deleteObject("p123")
      } { v =>
        v shouldBe akka.Done
        whenReady {
          stateStoreString.getObject("p123")
        } { v =>
          v.value shouldBe None
        }
      }
    }
  }

  "A durable state store with payload that needs custom serializer" must {
    "not load a state given an invalid persistenceId" in {
      whenReady {
        stateStorePayload.getObject("InvalidPersistenceId")
      } { v =>
        v.value shouldBe None
      }
    }
    "add a valid state successfully" in {
      whenReady {
        stateStorePayload.upsertObject("p123", 1, MyPayload("a valid string"), "t123")
      } { v =>
        v shouldBe akka.Done
      }
    }
    "support composite upsert-fetch-repeat loop" in {
      whenReady {
        for {

          n <- stateStorePayload.upsertObject("p234", 1, MyPayload("a valid string"), "t123")
          _ = n shouldBe akka.Done
          g <- stateStorePayload.getObject("p234")
          _ = g.value shouldBe Some(MyPayload("a valid string"))
          u <- stateStorePayload.upsertObject("p234", 2, MyPayload("updated valid string"), "t123")
          _ = u shouldBe akka.Done
          h <- stateStorePayload.getObject("p234")

        } yield h
      } { v =>
        v.value shouldBe Some(MyPayload("updated valid string"))
      }
    }
    "delete an existing state" in {
      whenReady {
        stateStorePayload.deleteObject("p234")
      } { v =>
        v shouldBe akka.Done
        whenReady {
          stateStorePayload.getObject("p234")
        } { v =>
          v.value shouldBe None
        }
      }
    }
  }
}

class H2DurableStateSpec extends JdbcDurableStateSpec(ConfigFactory.load("h2-application.conf"), H2) {
  final val stateStoreString =
    new JdbcDurableStateStore[String](db, slick.jdbc.H2Profile, durableStateConfig, serialization)
  final val stateStorePayload =
    new JdbcDurableStateStore[MyPayload](db, slick.jdbc.H2Profile, durableStateConfig, serialization)
}
