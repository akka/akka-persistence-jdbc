/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2025 Lightbend Inc. <https://akka.io>
 */

package akka.persistence.jdbc.migrator

import akka.actor.{ ActorRef, ActorSystem, Props, Stash }
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.persistence.jdbc.SimpleSpec
import akka.persistence.jdbc.config.{ JournalConfig, SlickConfiguration }
import akka.persistence.jdbc.db.SlickDatabase
import akka.persistence.jdbc.migrator.MigratorSpec._
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.jdbc.testkit.internal._
import akka.persistence.journal.EventSeq.single
import akka.persistence.journal.{ EventAdapter, EventSeq, Tagged }
import akka.persistence.query.PersistenceQuery
import akka.persistence.{ PersistentActor, SaveSnapshotSuccess, SnapshotMetadata, SnapshotOffer }
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory, ConfigValue, ConfigValueFactory }
import org.scalatest.BeforeAndAfterEach
import org.slf4j.{ Logger, LoggerFactory }
import slick.jdbc.JdbcBackend.{ Database, Session }

import java.sql.Statement
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ ExecutionContextExecutor, Future }

abstract class MigratorSpec(val config: Config) extends SimpleSpec with BeforeAndAfterEach {

  // The db is initialized in the before and after each bocks
  var dbOpt: Option[Database] = None

  implicit val pc: PatienceConfig = PatienceConfig(timeout = 10.seconds)
  implicit val timeout: Timeout = Timeout(1.minute)

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private val cfg: Config = config.getConfig("jdbc-journal")
  private val journalConfig: JournalConfig = new JournalConfig(cfg)

  protected val newJournalTableName: String = journalConfig.eventJournalTableConfiguration.tableName
  protected val legacyJournalTableName: String = journalConfig.journalTableConfiguration.tableName

  protected val newTables: Seq[String] =
    List(journalConfig.eventTagTableConfiguration.tableName, journalConfig.eventJournalTableConfiguration.tableName)
  protected val legacyTables: Seq[String] = List(journalConfig.journalTableConfiguration.tableName)
  protected val tables: Seq[String] = legacyTables ++ newTables

  def this(config: String = "postgres-application.conf", configOverrides: Map[String, ConfigValue] = Map.empty) =
    this(configOverrides.foldLeft(ConfigFactory.load(config)) { case (conf, (path, configValue)) =>
      conf.withValue(path, configValue)
    })

  def db: Database = dbOpt.getOrElse {
    val db = SlickDatabase.database(cfg, new SlickConfiguration(cfg.getConfig("slick")), "slick.db")
    dbOpt = Some(db)
    db
  }

  protected def dropAndCreate(schemaType: SchemaType): Unit = {
    // blocking calls, usually done in our before test methods
    // legacy
    SchemaUtilsImpl.dropWithSlick(schemaType, logger, db, legacy = true)
    SchemaUtilsImpl.createWithSlick(schemaType, logger, db, legacy = true)
    // new
    SchemaUtilsImpl.dropWithSlick(schemaType, logger, db, legacy = false)
    SchemaUtilsImpl.createWithSlick(schemaType, logger, db, legacy = false)
  }

  def withSession[A](f: Session => A)(db: Database): A = {
    val session = db.createSession()
    try f(session)
    finally session.close()
  }

  def withStatement[A](f: Statement => A)(db: Database): A =
    withSession(session => session.withStatement()(f))(db)

  def closeDb(): Unit = {
    dbOpt.foreach(_.close())
    dbOpt = None
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    closeDb()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    closeDb()
  }

  protected def setupEmpty(persistenceId: Int)(implicit system: ActorSystem): ActorRef =
    system.actorOf(Props(new TestAccountActor(persistenceId)))

  def withTestActors(seq: Int = 1)(f: (ActorRef, ActorRef, ActorRef) => Unit)(implicit system: ActorSystem): Unit = {
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    val refs = (seq until seq + 3).map(setupEmpty).toList
    try {
      // make sure we notice early if the actors failed to start (because of issues with journal) makes debugging
      // failing tests easier as we know it is not the actual interaction from the test that is the problem
      Future.sequence(refs.map(_ ? State)).futureValue

      f(refs.head, refs.drop(1).head, refs.drop(2).head)
    } finally killActors(refs: _*)
  }

  def withActorSystem(f: ActorSystem => Unit): Unit = {
    implicit val system: ActorSystem = ActorSystem("migrator-test", config)
    f(system)
    system.terminate().futureValue
  }

  def withLegacyActorSystem(f: ActorSystem => Unit): Unit = {

    val configOverrides: Map[String, ConfigValue] = Map(
      "jdbc-journal.dao" -> ConfigValueFactory.fromAnyRef(
        "akka.persistence.jdbc.journal.dao.legacy.ByteArrayJournalDao"),
      "jdbc-snapshot-store.dao" -> ConfigValueFactory.fromAnyRef(
        "akka.persistence.jdbc.snapshot.dao.legacy.ByteArraySnapshotDao"),
      "jdbc-read-journal.dao" -> ConfigValueFactory.fromAnyRef(
        "akka.persistence.jdbc.query.dao.legacy.ByteArrayReadJournalDao"))

    val legacyDAOConfig = configOverrides.foldLeft(ConfigFactory.load(config)) { case (conf, (path, configValue)) =>
      conf.withValue(path, configValue)
    }

    implicit val system: ActorSystem = ActorSystem("migrator-test", legacyDAOConfig)
    f(system)
    system.terminate().futureValue
  }

  def withReadJournal(f: JdbcReadJournal => Unit)(implicit system: ActorSystem): Unit = {
    val readJournal: JdbcReadJournal =
      PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)
    f(readJournal)
  }

  def countJournal(filterPid: String => Boolean = _ => true)(
      implicit system: ActorSystem,
      mat: Materializer,
      readJournal: JdbcReadJournal): Future[Long] =
    readJournal
      .currentPersistenceIds()
      .filter(filterPid(_))
      .mapAsync(1) { pid =>
        readJournal
          .currentEventsByPersistenceId(pid, 0, Long.MaxValue)
          .map(_ => 1L)
          .runWith(Sink.seq)
          .map(_.sum)(system.dispatcher)
      }
      .runWith(Sink.seq)
      .map(_.sum)(system.dispatcher)

  def eventsByTag(tag: String)(implicit mat: Materializer, readJournal: JdbcReadJournal): Future[Seq[AccountEvent]] =
    readJournal
      .currentEventsByTag(tag, offset = 0)
      .map(_.event)
      .collect { case e: AccountEvent =>
        e
      }
      .runWith(Sink.seq)

  def events(filterPid: String => Boolean = _ => true)(
      implicit mat: Materializer,
      readJournal: JdbcReadJournal): Future[Seq[Seq[AccountEvent]]] =
    readJournal
      .currentPersistenceIds()
      .filter(filterPid(_))
      .mapAsync(1) { pid =>
        readJournal
          .currentEventsByPersistenceId(pid, fromSequenceNr = 0, toSequenceNr = Long.MaxValue)
          .map(e => e.event)
          .collect { case e: AccountEvent =>
            e
          }
          .runWith(Sink.seq)
      }
      .runWith(Sink.seq)

}

object MigratorSpec {

  private final val Zero: Int = 0

  private final val SnapshotInterval: Int = 10

  val Even: String = "EVEN"
  val Odd: String = "ODD"

  /** Commands */
  sealed trait AccountCommand extends Serializable

  final case class CreateAccount(amount: Int) extends AccountCommand

  final case class Deposit(amount: Int) extends AccountCommand

  final case class Withdraw(amount: Int) extends AccountCommand

  object State extends AccountCommand

  /** Events */
  sealed trait AccountEvent extends Serializable {
    val amount: Int
  }

  final case class AccountCreated(override val amount: Int) extends AccountEvent

  final case class Deposited(override val amount: Int) extends AccountEvent

  final case class Withdrawn(override val amount: Int) extends AccountEvent

  /** Reply */
  final case class CurrentBalance(balance: Int)

  class AccountEventAdapter extends EventAdapter {

    override def manifest(event: Any): String = event.getClass.getSimpleName

    def fromJournal(event: Any, manifest: String): EventSeq = event match {
      case event: AccountEvent => single(event)
      case _                   => sys.error(s"Unexpected case '${event.getClass.getName}'")
    }

    def toJournal(event: Any): Any = event match {
      case event: AccountEvent =>
        val tag: String = if (event.amount % 2 == 0) Even else Odd
        Tagged(event, Set(tag))
      case _ => sys.error(s"Unexpected case '${event.getClass.getName}'")
    }
  }

  /** Actor */
  class TestAccountActor(id: Int) extends PersistentActor with Stash {
    override val persistenceId: String = s"test-account-$id"

    var state: Int = Zero

    private def saveSnapshot(): Unit = {
      if (state % SnapshotInterval == 0) {
        saveSnapshot(state)
      }
    }

    override def receiveCommand: Receive =
      LoggingReceive {

        case SaveSnapshotSuccess(_: SnapshotMetadata) => ()

        case CreateAccount(balance) =>
          persist(AccountCreated(balance)) { (event: AccountCreated) =>
            updateState(event)
            saveSnapshot()
            sender() ! akka.actor.Status.Success(event)
          }
        case Deposit(balance) =>
          persist(Deposited(balance)) { (event: Deposited) =>
            updateState(event)
            saveSnapshot()
            sender() ! akka.actor.Status.Success(event)
          }
        case Withdraw(balance) =>
          persist(Withdrawn(balance)) { (event: Withdrawn) =>
            updateState(event)
            saveSnapshot()
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
      LoggingReceive {
        case SnapshotOffer(_, snapshot: Int) =>
          state = snapshot
        case event: AccountEvent => updateState(event)
      }
  }

  trait PostgresCleaner extends MigratorSpec {

    def clearPostgres(): Unit = {
      tables.foreach { name =>
        withStatement(stmt => stmt.executeUpdate(s"DELETE FROM $name"))(db)
      }
    }

    override def beforeAll(): Unit = {
      dropAndCreate(Postgres)
      super.beforeAll()
    }

    override def beforeEach(): Unit = {
      dropAndCreate(Postgres)
      super.beforeEach()
    }
  }

  trait MysqlCleaner extends MigratorSpec {

    def clearMySQL(): Unit = {
      withStatement { stmt =>
        stmt.execute("SET FOREIGN_KEY_CHECKS = 0")
        tables.foreach { name => stmt.executeUpdate(s"TRUNCATE $name") }
        stmt.execute("SET FOREIGN_KEY_CHECKS = 1")
      }(db)
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

  trait OracleCleaner extends MigratorSpec {

    def clearOracle(): Unit = {
      tables.foreach { name =>
        withStatement(stmt => stmt.executeUpdate(s"""DELETE FROM "$name" """))(db)
      }
      withStatement(stmt => stmt.executeUpdate("""BEGIN "reset_sequence"; END; """))(db)
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

  trait SqlServerCleaner extends MigratorSpec {

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
        stmt.executeUpdate(s"DBCC CHECKIDENT('$legacyJournalTableName', RESEED, $reset)")
        stmt.executeUpdate(s"DBCC CHECKIDENT('$newJournalTableName', RESEED, $reset)")
      }(db)
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

  trait H2Cleaner extends MigratorSpec {

    def clearH2(): Unit = {
      tables.foreach { name =>
        withStatement(stmt => stmt.executeUpdate(s"DELETE FROM $name"))(db)
      }
    }

    override def beforeEach(): Unit = {
      dropAndCreate(H2)
      super.beforeEach()
    }
  }
}
