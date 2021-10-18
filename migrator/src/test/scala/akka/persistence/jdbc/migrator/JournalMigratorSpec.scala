package akka.persistence.jdbc.migrator

import akka.actor.{ ActorRef, ActorSystem, Props, Stash }
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.persistence.PersistentActor
import akka.persistence.jdbc.SingleActorSystemPerTestSpec
import akka.persistence.jdbc.migrator.JournalMigratorSpec._
import akka.persistence.jdbc.query.CurrentEventsByTagTest.configOverrides
import akka.persistence.jdbc.testkit.internal._

import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.concurrent.duration.DurationInt

abstract class JournalMigratorSpec(config: String) extends SingleActorSystemPerTestSpec(config, configOverrides) {

  override implicit val pc: PatienceConfig = PatienceConfig(timeout = 5.seconds)

  protected def setupEmpty(persistenceId: Int)(implicit system: ActorSystem): ActorRef =
    system.actorOf(Props(new TestAccountActor(persistenceId)))

  def withTestActors(seq: Int = 1)(f: (ActorRef, ActorRef, ActorRef) => Unit)(implicit system: ActorSystem): Unit = {
    val refs = (seq until seq + 3).map(setupEmpty).toList
    try {
      expectAllStarted(refs)
      f(refs.head, refs.drop(1).head, refs.drop(2).head)
    } finally killActors(refs: _*)
  }

  def withManyTestActors(amount: Int, seq: Int = 1)(f: Seq[ActorRef] => Unit)(implicit system: ActorSystem): Unit = {
    val refs = (seq until seq + amount).map(setupEmpty).toList
    try {
      expectAllStarted(refs)
      f(refs)
    } finally killActors(refs: _*)
  }

  def expectAllStarted(refs: Seq[ActorRef])(implicit system: ActorSystem): Unit = {
    // make sure we notice early if the actors failed to start (because of issues with journal) makes debugging
    // failing tests easier as we know it is not the actual interaction from the test that is the problem
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    Future.sequence(refs.map(_ ? State)).futureValue
  }
}

object JournalMigratorSpec {

  private final val Zero = BigDecimal(0)

  /** commands */
  sealed trait AccountCommand extends Serializable

  final case class CreateAccount(amount: BigDecimal) extends AccountCommand

  final case class Deposit(amount: BigDecimal) extends AccountCommand

  final case class Withdraw(amount: BigDecimal) extends AccountCommand

  final object State extends AccountCommand

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
        case State =>
          sender() ! akka.actor.Status.Success(state)
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

  trait PostgresCleaner extends JournalMigratorSpec {

    def clearPostgres(): Unit =
      tables.foreach { name => withStatement(stmt => stmt.executeUpdate(s"DELETE FROM $name")) }

    override def beforeAll(): Unit = {
      dropAndCreate(Postgres)
      super.beforeAll()
    }

    override def beforeEach(): Unit = {
      dropAndCreate(Postgres)
      super.beforeEach()
    }
  }

  trait MysqlCleaner extends JournalMigratorSpec {

    def clearMySQL(): Unit = {
      withStatement { stmt =>
        stmt.execute("SET FOREIGN_KEY_CHECKS = 0")
        tables.foreach { name => stmt.executeUpdate(s"TRUNCATE $name") }
        stmt.execute("SET FOREIGN_KEY_CHECKS = 1")
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

  trait OracleCleaner extends JournalMigratorSpec {

    def clearOracle(): Unit = {
      tables.foreach { name =>
        withStatement(stmt => stmt.executeUpdate(s"""DELETE FROM "$name" """))
      }
      withStatement(stmt => stmt.executeUpdate("""BEGIN "reset_sequence"; END; """))
    }

    override def beforeAll(): Unit = {
      dropAndCreate(Oracle)
      super.beforeAll()
    }

    override def beforeEach(): Unit = {
      clearOracle()
      super.beforeEach()
    }
  }

  trait SqlServerCleaner extends JournalMigratorSpec {

    var initial = true

    def clearSqlServer(): Unit = {
      val reset = if (initial) {
        initial = false
        1
      } else {
        0
      }
      withStatement { stmt =>
        tables.foreach { name => stmt.executeUpdate(s"DELETE FROM $name") }
        stmt.executeUpdate(s"DBCC CHECKIDENT('$journalTableName', RESEED, $reset)")
      }
    }

    override def beforeAll(): Unit = {
      dropAndCreate(SqlServer)
      super.beforeAll()
    }

    override def afterAll(): Unit = {
      dropAndCreate(SqlServer)
      super.afterAll()
    }

    override def beforeEach(): Unit = {
      clearSqlServer()
      super.beforeEach()
    }
  }

  trait H2Cleaner extends JournalMigratorSpec {

    def clearH2(): Unit =
      tables.foreach { name => withStatement(stmt => stmt.executeUpdate(s"DELETE FROM $name")) }

    override def beforeEach(): Unit = {
      dropAndCreate(H2)
      super.beforeEach()
    }
  }
}
