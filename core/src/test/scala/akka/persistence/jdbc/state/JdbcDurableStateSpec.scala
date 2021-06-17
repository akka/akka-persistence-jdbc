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
import akka.persistence.query.{ NoOffset, Offset, Sequence }
import akka.persistence.query.DurableStateChange
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.Sink

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
      import org.scalatest.time._
  implicit val defaultPatience =
    PatienceConfig(timeout = Span(20, Seconds), interval = Span(5, Millis))

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

  "A JDBC durable state store" must {
    import stateStoreString._
    def upserts() = {
      upsertObject("p1", 1, "1 valid string", "t1").futureValue // offset = 1
      upsertObject("p1", 2, "2 valid string", "t1").futureValue // offset = 2
      upsertObject("p2", 1, "1 valid string", "t1").futureValue // offset = 3
      upsertObject("p3", 1, "1 valid string", "t2").futureValue // offset = 4
      upsertObject("p2", 2, "2 valid string", "t1").futureValue // offset = 5
      upsertObject("p1", 3, "3 valid string", "t1").futureValue // offset = 6
      upsertObject("p2", 3, "3 valid string", "t1").futureValue // offset = 7
      upsertObject("p1", 4, "4 valid string", "t1").futureValue // offset = 8
    }
    "support query for current changes by tags and return last offsets seen" in {
      upserts()
      val source = stateStoreString.currentChanges("t1", NoOffset)
      whenReady {
        source.runFold(List.empty[DurableStateChange[String]]) { (acc, chg) => acc ++ List(chg) }
      } { v =>
        v.size shouldBe 2
        val states = v.foldLeft(Map.empty[String, (Offset, Long)])((acc, e) => acc + ((e.persistenceId, (e.offset, e.seqNr)))) 
        states.get("p1").map(e => e._1 === Sequence(8) && e._2 === 4)
        states.get("p2").map(e => e._1 === Sequence(7) && e._2 === 3)
      }

      // push more data
      upsertObject("p1", 5, "5 valid string", "t1").futureValue
      upsertObject("p1", 6, "6 valid string", "t1").futureValue
      upsertObject("p1", 7, "7 valid string", "t1").futureValue
      upsertObject("p1", 8, "8 valid string", "t1").futureValue

      // new offsets
      whenReady {
        source.runFold(List.empty[DurableStateChange[String]]) { (acc, chg) => acc ++ List(chg) }
      } { v =>
        v.size shouldBe 2
        val states = v.foldLeft(Map.empty[String, (Offset, Long)])((acc, e) => acc + ((e.persistenceId, (e.offset, e.seqNr)))) 
        states.get("p1").map(e => e._1 === Sequence(12) && e._2 === 8)
        states.get("p2").map(e => e._1 === Sequence(7) && e._2 === 3)
      }
    }
    "support query for current changes by tags with offset specified" in {
      upserts()
      val source = stateStoreString.currentChanges("t1", Offset.sequence(7))
      whenReady {
        source.runFold(List.empty[DurableStateChange[String]]) { (acc, chg) => acc ++ List(chg) }
      } { v =>
        v.size shouldBe 1
        v.map(_.persistenceId).toSet shouldBe Set("p1")
        v.map(_.seqNr).toSet shouldBe Set(4)
        v.map(_.offset).toSet shouldBe Set(Sequence(8))
      }
    }
    def upsertsForChange() = {
        upsertObject("p1", 1, "1 valid string", "t1").futureValue
        upsertObject("p1", 2, "2 valid string", "t1").futureValue
        upsertObject("p2", 1, "1 valid string", "t1").futureValue
        upsertObject("p3", 1, "1 valid string", "t2").futureValue
        upsertObject("p2", 2, "2 valid string", "t1").futureValue
        upsertObject("p1", 3, "3 valid string", "t1").futureValue
        upsertObject("p2", 3, "3 valid string", "t1").futureValue
        upsertObject("p1", 4, "4 valid string", "t1").futureValue
        upsertObject("p1", 5, "5 valid string", "t1").futureValue
        upsertObject("p2", 4, "4 valid string", "t1").futureValue
        upsertObject("p3", 2, "2 valid string", "t2").futureValue
        upsertObject("p2", 5, "5 valid string", "t1").futureValue
        upsertObject("p1", 6, "6 valid string", "t1").futureValue
    }
    def moreUpsertsForChange() = {
        upsertObject("p3", 3, "3 valid string", "t2").futureValue
        upsertObject("p2", 6, "6 valid string", "t1").futureValue
        upsertObject("p1", 7, "7 valid string", "t1").futureValue
    }
    "support query for changes by tags with no offset specified" in {
      upsertsForChange()
      val source = stateStoreString.changes("t1", NoOffset)
      val m = collection.mutable.ListBuffer.empty[(String, Offset)]
      val f = source.map(ds => m += ((ds.persistenceId, ds.offset))).runWith(Sink.ignore)

      // more data after some delay
      Thread.sleep(1000)
      moreUpsertsForChange()

      whenReady(f) { _ => 
        m.size shouldBe 4
        m.toList.toSet shouldBe Set(("p1", Sequence(13)), ("p2", Sequence(12)), ("p1", Sequence(16)), ("p2", Sequence(15)))
      }
    }
    "support query for changes by tags with an offset specified" in {
      upsertsForChange()
      val source = stateStoreString.changes("t1", Sequence(12))
      val z = source.runWith(Sink.seq).futureValue 
      val elems = z.map(a => (a.persistenceId, a.offset, a.seqNr))
      elems.size shouldBe 1
      elems.map(_._2).toSet shouldBe Set(Sequence(13))
      elems.map(_._1).toSet shouldBe Set("p1")
    }
  }
}

class H2DurableStateSpec extends JdbcDurableStateSpec(ConfigFactory.load("h2-application.conf"), H2) {
  final val stateStoreString =
    new JdbcDurableStateStore[String](db, slick.jdbc.H2Profile, durableStateConfig, serialization)
  final val stateStorePayload =
    new JdbcDurableStateStore[MyPayload](db, slick.jdbc.H2Profile, durableStateConfig, serialization)
}
