package akka.persistence.jdbc.actor

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.jdbc.common.PluginConfig
import akka.persistence.jdbc.extension.ScalikeExtension
import akka.persistence.jdbc.util._
import akka.persistence.{SnapshotOffer, PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess}
import akka.testkit.{TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpecLike}
import scalikejdbc.DBSession

object TestActor {
  case object Snap
  case object GetState
  case class Alter(id: String)

  case class TheState(id: String = "")
}

class TestActor(testProbe: ActorRef) extends PersistentActor with ActorLogging {
  import TestActor._
  override def persistenceId: String = "TestActor"

  var state = TheState()

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, snap: TheState) =>
      log.info("Recovering snapshot: {}", snap)
      state = snap
    case m: Alter =>
      log.info("Recovering journal: {}", m)
      state = state.copy(id = m.id)
  }

  override def receiveCommand: Receive = {
    case Snap =>
      saveSnapshot(state)
    case m: Alter =>
      // save journal entry first (alter state)
      persist(m) {
        case Alter(m) => state = state.copy(id = m)
      }
      saveSnapshot(state) // note: first the snapshot will be created = TheState("")
                          // later, but *before* dispatcher will get the next message,
                          // the event handler will be executed, a journal entry "a" will be
                          // written.. thus the snapshot will *not* hold "a", but ""
    case msg: SaveSnapshotFailure =>
      testProbe ! "f"
    case msg: SaveSnapshotSuccess =>
      testProbe ! "s"
    case GetState => sender ! state
  }
}

trait ActorTest extends FlatSpecLike with BeforeAndAfterEach with BeforeAndAfterAll with JdbcInit {
  import TestActor._
  implicit val system: ActorSystem
  val cfg  = PluginConfig(system)

  val testProbe = TestProbe()


  "snapshot store" should "be able to save multiple snapshots with the same state" in {
    val test = system.actorOf(Props(new TestActor(testProbe.ref)))

    test ! Snap
    test ! Snap

    testProbe.expectMsgAllOf("s", "s")

    // kill the actor
    system.stop(test)
    testProbe watch test
    testProbe.expectTerminated(test)

    // get persisted state
    val test2 = system.actorOf(Props(new TestActor(testProbe.ref)))
    testProbe.send(test2, GetState)

    testProbe.expectMsg(TheState(id = ""))

    system.stop(test2)
    testProbe watch test2
    testProbe.expectTerminated(test2)
  }

  it should "be able to save alternate snapshot states" in {
    val test = system.actorOf(Props(new TestActor(testProbe.ref)))

    test ! Alter("a") // note the stored snapshot = TheState("")
    test ! Alter("b") // note the stored snapshot = TheState("a"),
                      // the journal entry will be used to get to "b"

    testProbe.expectMsgAllOf("s", "s")

    // kill the actor
    system.stop(test)
    testProbe watch test
    testProbe.expectTerminated(test)

    // get persisted state
    val test2 = system.actorOf(Props(new TestActor(testProbe.ref)))
    testProbe.send(test2, GetState)

    testProbe.expectMsg(TheState(id = "b"))

    system.stop(test2)
    testProbe watch test2
    testProbe.expectTerminated(test2)
  }


  override protected def beforeAll(): Unit = {
    super.beforeAll()
  }

  override protected def beforeEach(): Unit = {
    clearJournalTable()
    clearSnapshotTable()
    super.beforeEach()
  }

  override def afterAll() = {
    system.shutdown()
  }
}

trait GenericActorTest extends ActorTest with GenericJdbcInit {
  override implicit val session: DBSession = ScalikeExtension(system).session
}

class PostgresActorTest extends TestKit(ActorSystem("TestCluster", ConfigFactory.load("postgres-application.conf"))) with GenericActorTest with PostgresqlJdbcInit

class OracleActorTest extends TestKit(ActorSystem("TestCluster", ConfigFactory.load("oracle-application.conf"))) with GenericActorTest with OracleJdbcInit

class MySqlActorTest extends TestKit(ActorSystem("TestCluster", ConfigFactory.load("mysql-application.conf"))) with GenericActorTest with MysqlJdbcInit

class H2ActorTest extends TestKit(ActorSystem("TestCluster", ConfigFactory.load("h2-application.conf"))) with GenericActorTest with H2JdbcInit

class InformixActorTest extends TestKit(ActorSystem("TestCluster", ConfigFactory.load("informix-application.conf"))) with GenericActorTest with InformixJdbcInit
