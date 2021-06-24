package akka.persistence.jdbc.state.scaladsl

import com.typesafe.config.{ Config, ConfigFactory }

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time._

import akka.actor._
import akka.persistence.jdbc.db.SlickDatabase
import akka.persistence.jdbc.config._
import akka.persistence.jdbc.state.{ MyPayload, OffsetSyntax }
import OffsetSyntax._
import akka.persistence.jdbc.testkit.internal.{ H2, Postgres, SchemaType }
import akka.persistence.jdbc.util.DropCreate
import akka.persistence.query.{ NoOffset, Offset, Sequence }
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.Sink

abstract class JdbcDurableStateSpec(val config: Config, schemaType: SchemaType)
    extends AnyWordSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers
    with ScalaFutures
    with DropCreate
    with DataGenerationHelper {

  implicit def system: ActorSystem

  implicit lazy val e = system.dispatcher

  private def schemaTypeToProfile(s: SchemaType) = s match {
    case H2       => slick.jdbc.H2Profile
    case Postgres => slick.jdbc.PostgresProfile
    case _        => ???
  }

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

  val customConfig = ConfigFactory.parseString(s"""
    jdbc-durable-state-store {
      schemaType = H2
      stopAfterEmptyFetchIterations = 5
      batchSize = 200
      refreshInterval = 500.milliseconds
      durable-state-sequence-retrieval {
        query-delay = 100.milliseconds
      }
    }
  """)

  lazy val cfg = customConfig
    .getConfig("jdbc-durable-state-store")
    .withFallback(system.settings.config.getConfig("jdbc-durable-state-store"))
    .withFallback(config.getConfig("jdbc-durable-state-store"))
    .withFallback(customSerializers.getConfig("akka.actor"))

  lazy val db = SlickDatabase.database(cfg, new SlickConfiguration(cfg.getConfig("slick")), "slick.db")
  lazy val durableStateConfig = new DurableStateTableConfiguration(cfg)
  lazy val serialization = SerializationExtension(system)

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(60, Seconds), interval = Span(10, Millis))

  def withActorSystem(f: ExtendedActorSystem => Unit): Unit = {
    implicit val system: ExtendedActorSystem =
      ActorSystem("JdbcDurableStateSpec", config.withFallback(customSerializers)).asInstanceOf[ExtendedActorSystem]
    f(system)
    system.terminate().futureValue
  }

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
    system.terminate().futureValue
  }

  "A durable state store" must withActorSystem { implicit system =>
    val stateStoreString =
      new JdbcDurableStateStore[String](db, schemaTypeToProfile(schemaType), durableStateConfig, serialization)

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
        schemaType match {
          case H2 =>
            e shouldBe an[org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException]
          case Postgres =>
            e shouldBe an[org.postgresql.util.PSQLException]
        }
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

  "A durable state store with payload that needs custom serializer" must withActorSystem { implicit system =>
    val stateStorePayload =
      new JdbcDurableStateStore[MyPayload](db, schemaTypeToProfile(schemaType), durableStateConfig, serialization)

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

    "find all states by tag either from the beginning or from a specific offset" in withActorSystem { implicit system =>
      val stateStoreString =
        new JdbcDurableStateStore[String](db, schemaTypeToProfile(schemaType), durableStateConfig, serialization)

      import stateStoreString._

      // fetch from beginning
      upsertManyFor(stateStoreString, "p1", "t1", 1, 4)
      val chgs = currentChanges("t1", NoOffset).runWith(Sink.seq).futureValue
      chgs.size shouldBe 1
      chgs.map(_.offset.value).max shouldBe 4

      // upsert more and fetch from after the last offset
      upsertManyFor(stateStoreString, "p1", "t1", 5, 7)
      val moreChgs = currentChanges("t1", chgs.head.offset).runWith(Sink.seq).futureValue
      moreChgs.size shouldBe 1
      moreChgs.map(_.offset.value).max shouldBe 11

      // upsert same tag, different peristence id and fetch from after the last offset
      upsertManyFor(stateStoreString, "p2", "t1", 1, 3)
      val otherChgs = currentChanges("t1", moreChgs.head.offset).runWith(Sink.seq).futureValue
      otherChgs.size shouldBe 1
      otherChgs.map(_.offset.value).max shouldBe 14

      // again fetch from the beginning
      val cs = currentChanges("t1", NoOffset).runWith(Sink.seq).futureValue
      cs.size shouldBe 2
      cs.map(_.offset.value).max shouldBe 14
    }

    "find the max offset after a series of upserts with multiple persistence ids" in withActorSystem {
      implicit system =>
        val stateStoreString =
          new JdbcDurableStateStore[String](db, schemaTypeToProfile(schemaType), durableStateConfig, serialization)

        import stateStoreString._
        upsertRandomlyShuffledPersistenceIds(stateStoreString, List("p1", "p2", "p3"), "t1", 3)
        val chgs = currentChanges("t1", NoOffset).runWith(Sink.seq).futureValue
        chgs.size shouldBe 3
        chgs.map(_.offset.value).max shouldBe 9
    }

    "find all states by tags with offsets sorted and proper max and min offsets when starting offset is specified" in withActorSystem {
      implicit system =>
        val stateStoreString =
          new JdbcDurableStateStore[String](db, schemaTypeToProfile(schemaType), durableStateConfig, serialization)

        import stateStoreString._
        upsertRandomlyShuffledPersistenceIds(stateStoreString, List("p1", "p2", "p3"), "t1", 3)
        val chgs = stateStoreString.currentChanges("t1", Offset.sequence(7)).runWith(Sink.seq).futureValue
        chgs.map(_.offset.value) shouldBe sorted
        chgs.map(_.offset.value).max shouldBe 9
        chgs.map(_.offset.value).min should be > 7L
    }

    "find all states by tags returning a live source with no offset specified" in withActorSystem { implicit system =>
      val stateStoreString =
        new JdbcDurableStateStore[String](db, schemaTypeToProfile(schemaType), durableStateConfig, serialization)

      import stateStoreString._
      upsertRandomlyShuffledPersistenceIds(stateStoreString, List("p1", "p2", "p3"), "t1", 3)
      val source = stateStoreString.changes("t1", NoOffset)
      val m = collection.mutable.ListBuffer.empty[(String, Offset)]
      val f = source.map(ds => m += ((ds.persistenceId, ds.offset))).runWith(Sink.ignore)

      // more data after some delay
      Thread.sleep(100)
      upsertObject("p3", 4, "4 valid string", "t2").futureValue
      upsertObject("p2", 4, "4 valid string", "t1").futureValue
      upsertObject("p1", 4, "4 valid string", "t1").futureValue

      whenReady(f) { _ =>
        m.size shouldBe 2
        m.toList.map(_._2.value) shouldBe sorted
        m.toList.map(_._2.value).max shouldBe 12
      }
    }

    "find all states by tags returning a live source with a starting offset specified" in withActorSystem {
      implicit system =>
        val stateStoreString =
          new JdbcDurableStateStore[String](db, schemaTypeToProfile(schemaType), durableStateConfig, serialization)

        import stateStoreString._
        upsertRandomlyShuffledPersistenceIds(stateStoreString, List("p1", "p2", "p3"), "t1", 3)
        val source = stateStoreString.changes("t1", Sequence(4))
        val m = collection.mutable.ListBuffer.empty[(String, Offset)]
        val z = source.map(ds => m += ((ds.persistenceId, ds.offset))).runWith(Sink.ignore)

        // more data after some delay
        Thread.sleep(100)
        upsertManyFor(stateStoreString, "p3", "t1", 4, 3)

        whenReady(z) { _ =>
          m.map(_._2.value) shouldBe sorted
          m.map(_._2.value).min should be > 4L
          m.map(_._2.value).max shouldBe 12
        }
    }
  }

  "A JDBC durable state store in the face of parallel upserts" must {

    "fetch proper values of offsets with currentChanges()" in withActorSystem { implicit system =>
      val stateStoreString =
        new JdbcDurableStateStore[String](db, schemaTypeToProfile(schemaType), durableStateConfig, serialization)

      import stateStoreString._

      upsertParallel(stateStoreString, Set("p1", "p2", "p3"), "t1", 1000)(e).futureValue
      whenReady {
        currentChanges("t1", NoOffset).runWith(Sink.seq)
      } { chgs =>
        chgs.map(_.offset.value) shouldBe sorted
        chgs.map(_.offset.value).max shouldBe 3000
      }

      whenReady {
        currentChanges("t1", Sequence(2000)).runWith(Sink.seq)
      } { chgs =>
        chgs.map(_.offset.value) shouldBe sorted
        chgs.map(_.offset.value).max shouldBe 3000
      }
    }

    "fetch proper values of offsets from beginning with changes() and phased upserts - case 1" in withActorSystem {
      implicit system =>
        val stateStoreString =
          new JdbcDurableStateStore[String](db, schemaTypeToProfile(schemaType), durableStateConfig, serialization)

        import stateStoreString._

        upsertParallel(stateStoreString, Set("p1", "p2", "p3"), "t1", 5)(e).futureValue
        val source = changes("t2", NoOffset)
        val m = collection.mutable.ListBuffer.empty[(String, Offset)]
        val z = source.map(ds => m += ((ds.persistenceId, ds.offset))).runWith(Sink.ignore)

        // more data after some delay
        Thread.sleep(1000)
        upsertManyFor(stateStoreString, "p3", "t1", 6, 3)
        Thread.sleep(1000)
        upsertManyFor(stateStoreString, "p3", "t2", 9, 3)

        whenReady(z) { _ =>
          m.map(_._2.value) shouldBe sorted
          m.map(_._2.value).min should be > 0L
          m.map(_._2.value).max shouldBe 21
        }
    }

    "fetch proper values of offsets from beginning for a larger dataset with changes() and phased upserts - case 2" in withActorSystem {
      implicit system =>
        val stateStoreString =
          new JdbcDurableStateStore[String](db, schemaTypeToProfile(schemaType), durableStateConfig, serialization)

        import stateStoreString._

        upsertParallel(stateStoreString, Set("p1", "p2", "p3"), "t1", 1000)(e).futureValue
        val source = changes("t2", NoOffset)
        val m = collection.mutable.ListBuffer.empty[(String, Offset)]
        val z = source.map(ds => m += ((ds.persistenceId, ds.offset))).runWith(Sink.ignore)

        // more data after some delay
        Thread.sleep(1000)
        upsertManyFor(stateStoreString, "p3", "t1", 1001, 30)
        Thread.sleep(1000)
        upsertManyFor(stateStoreString, "p3", "t2", 1031, 30)

        whenReady(z) { _ =>
          m.map(_._2.value) shouldBe sorted
          m.map(_._2.value).min should be > 0L
          m.map(_._2.value).max shouldBe 3060
        }
    }

    "fetch proper values of offsets from beginning for a larger dataset with changes() and phased upserts - case 3" in withActorSystem {
      implicit system =>
        val stateStoreString =
          new JdbcDurableStateStore[String](db, schemaTypeToProfile(schemaType), durableStateConfig, serialization)

        import stateStoreString._

        upsertParallel(stateStoreString, Set("p1", "p2", "p3"), "t1", 1000)(e).futureValue
        val source = changes("t1", NoOffset)
        val m = collection.mutable.ListBuffer.empty[(String, Offset)]
        val z = source.map(ds => m += ((ds.persistenceId, ds.offset))).runWith(Sink.ignore)

        // more data after some delay
        Thread.sleep(1000)
        upsertManyFor(stateStoreString, "p3", "t1", 1001, 30)
        Thread.sleep(1000)
        upsertManyFor(stateStoreString, "p3", "t2", 1031, 30)

        whenReady(z) { _ =>
          m.map(_._2.value) shouldBe sorted
          m.map(_._2.value).min should be > 0L
          m.map(_._2.value).max shouldBe 2000
        }
    }
  }
}

class H2DurableStateSpec extends JdbcDurableStateSpec(ConfigFactory.load("h2-application.conf"), H2) {
  implicit lazy val system: ActorSystem =
    ActorSystem("JdbcDurableStateSpec", config.withFallback(customSerializers))
}

class PostgresDurableStateSpec extends JdbcDurableStateSpec(ConfigFactory.load("postgres-application.conf"), Postgres) {
  implicit lazy val system: ActorSystem =
    ActorSystem("JdbcDurableStateSpec", config.withFallback(customSerializers))
}
