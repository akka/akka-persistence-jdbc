package akka.persistence.jdbc.migrator

import akka.actor.{ ActorRef, ActorSystem, Props, Stash }
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.persistence.PersistentActor
import akka.persistence.jdbc.SingleActorSystemPerTestSpec
import akka.persistence.jdbc.migrator.JournalMigratorSpec._
import akka.persistence.jdbc.query.CurrentEventsByTagTest.configOverrides
import akka.persistence.jdbc.query.ScalaJdbcReadJournalOperations
import akka.persistence.jdbc.testkit.internal.MySQL

// TODO TAG
// TODO not find an event by tag for unknown tag
// TODO QueryTestSpec
// TODO import akka.persistence.journal.{EventSeq, ReadEventAdapter, Tagged, WriteEventAdapter}
// TODO import org.scalatest.time.SpanSugar.convertIntToGrainOfTime

object JournalMigratorSpec {

  private final val Zero = BigDecimal(0)

  /** commands */
  sealed trait AccountCommand extends Serializable

  final case class CreateAccount(amount: BigDecimal) extends AccountCommand

  final case class Deposit(amount: BigDecimal) extends AccountCommand

  final case class Withdraw(amount: BigDecimal) extends AccountCommand

  final object GetBalance extends AccountCommand

  /** Reply */
  final case class CurrentBalance(balance: BigDecimal)

  /** events */
  sealed trait AccountEvent extends Serializable

  final case class AccountCreated(amount: BigDecimal) extends AccountEvent

  final case class Deposited(amount: BigDecimal) extends AccountEvent

  final case class Withdrawn(amount: BigDecimal) extends AccountEvent

  class TestAccountActor(id: Int) extends PersistentActor with Stash {
    override val persistenceId: String = s"actor-$id"

    var state: BigDecimal = Zero

    override def receiveCommand: Receive =
      LoggingReceive {

        case CreateAccount(balance) =>
          persist(AccountCreated(balance)) { (event: AccountCreated) =>
            updateState(event)
            sender() ! akka.actor.Status.Success(event)
          }
        case Deposit(balance) =>
          persist(Deposited(balance)) { (event: Deposited) =>
            updateState(event)
            sender() ! akka.actor.Status.Success(event)
          }
        case Withdraw(balance) =>
          persist(Withdrawn(balance)) { (event: Withdrawn) =>
            updateState(event)
            sender() ! akka.actor.Status.Success(event)
          }
        case GetBalance => sender() ! state
      }

    def updateState(event: AccountEvent): Unit = event match {
      case AccountCreated(amount) => state = state + amount
      case Deposited(amount)      => state = state + amount
      case Withdrawn(amount)      => state = state - amount
    }

    override def receiveRecover: Receive =
      LoggingReceive { case event: AccountEvent =>
        updateState(event)
      }
  }
}

abstract class JournalMigratorSpec(config: String) extends SingleActorSystemPerTestSpec(config, configOverrides) {

  protected def setupEmpty(persistenceId: Int)(implicit system: ActorSystem): ActorRef =
    system.actorOf(Props(new TestAccountActor(persistenceId)))

  protected def withTestActors(seq: Int = 1)(f: (ActorRef, ActorRef, ActorRef) => Unit)(
      implicit system: ActorSystem): Unit = {
    val refs: Seq[ActorRef] = (seq until seq + 3).map(setupEmpty).toList
    try f(refs.head, refs.drop(1).head, refs.drop(2).head)
    finally killActors(refs: _*)
  }

  protected def withManyTestActors(amount: Int, seq: Int = 1)(f: Seq[ActorRef] => Unit)(
      implicit system: ActorSystem): Unit = {
    val refs: Seq[ActorRef] = (seq until seq + amount).map(setupEmpty).toList
    try f(refs)
    finally killActors(refs: _*)
  }

  it should "TODO 1" in {
    pending
    withActorSystem { implicit system =>
      //val dao = new ByteArrayJournalDao(db, profile, journalConfig, SerializationExtension(system))

      val journalOps = new ScalaJdbcReadJournalOperations(system)
      withTestActors() { (actor1, actor2, actor3) =>
        (actor1 ? AccountCreated(1)).futureValue //balance 1
        (actor2 ? AccountCreated(2)).futureValue //balance 2
        (actor3 ? AccountCreated(3)).futureValue //balance 3
        (actor1 ? Deposit(3)).futureValue //balance 4
        (actor2 ? Deposit(2)).futureValue //balance 4
        (actor3 ? Deposit(1)).futureValue //balance 4
        (actor1 ? Withdrawn(3)).futureValue //balance 3
        (actor2 ? Withdrawn(3)).futureValue //balance 3
        (actor3 ? Withdrawn(3)).futureValue //balance 3

        eventually {
          journalOps.countJournal.futureValue shouldBe 20
        }

        system.terminate()
      }
      /* withActorSystem { implicit system =>
       val migrate = JournalMigrator(SlickDatabase.profile(cfg, "slick")).migrate()
         eventually {
           journalOps.countJournal.futureValue shouldBe 9
         }
         (actor1 ? AccountOpenedCommand(1)).futureValue
         (actor2 ? AccountOpenedCommand(2)).futureValue
         (actor3 ? AccountOpenedCommand(3)).futureValue
         assert(1 == 1)
         system.terminate()
       }*/
    }
  }
}

class MysqlJournalMigratorSpec extends JournalMigratorSpec("mysql-application.conf") {
  def clearMySQL(): Unit = {
    withStatement { statement =>
      statement.execute("SET FOREIGN_KEY_CHECKS = 0")
      tables.foreach(name => statement.executeUpdate(s"TRUNCATE $name"))
      statement.execute("SET FOREIGN_KEY_CHECKS = 1")
    }
  }

  override def beforeAll(): Unit = {
    dropAndCreate(MySQL)
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    clearMySQL()
    super.beforeEach()
  }
}
