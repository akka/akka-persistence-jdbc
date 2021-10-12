package akka.persistence.jdbc.migrator

import akka.actor.{ActorRef, ActorSystem, Props, Stash, Status}
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.persistence.jdbc.SingleActorSystemPerTestSpec
import akka.persistence.{DeleteMessagesFailure, DeleteMessagesSuccess, PersistentActor}
import akka.persistence.jdbc.db.SlickDatabase
import akka.persistence.jdbc.journal.dao.legacy.ByteArrayJournalDao
import akka.persistence.jdbc.query.CurrentEventsByTagTest.configOverrides
import akka.persistence.jdbc.query.EventAdapterTest.{Event, TaggedAsyncEvent, TaggedEvent}
import akka.persistence.jdbc.query.{EventAdapterTest, MysqlCleaner, QueryTestSpec, ScalaJdbcReadJournalOperations}
import akka.persistence.journal.{EventSeq, ReadEventAdapter, Tagged, WriteEventAdapter}
import akka.serialization.SerializationExtension
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime


abstract class BaseJournalMigratorSpec(config: String) extends SingleActorSystemPerTestSpec(config, configOverrides) {
  case class AccountOpenedCommand(balance: Int) extends Serializable
  case class AccountOpenedEvent(balance: Int){
    def adapted = AccountOpenedEventAdapted(value = balance)
  }
  case object GetBalanceCommand extends Serializable
  case class TaggedAccountOpenedEvent(event: AccountOpenedEvent, tag: String)

  case class TaggedAsyncAccountOpenedEvent(event: Event, tag: String)

  case class AccountOpenedEventAdapted(value: Int) {
    def restored = AccountOpenedEvent(value)
  }

  case class AccountOpenedEventRestored(value: String)

  class TestReadAccountOpenedEventAdapter extends ReadEventAdapter {
    override def fromJournal(event: Any, manifest: String): EventSeq =
      event match {
        case e: AccountOpenedEventAdapted => EventSeq.single(e.restored)
      }
  }

  class TestWriteEventAdapter extends WriteEventAdapter {
    override def manifest(event: Any): String = ""

    override def toJournal(event: Any): Any =
      event match {
        case e: AccountOpenedEvent           => e.adapted
        case TaggedEvent(e: Event, tag)      => Tagged(e.adapted, Set(tag))
        case TaggedAsyncEvent(e: Event, tag) => Tagged(e.adapted, Set(tag))
        case _                               => event
      }
  }

  final val ExpectNextTimeout = 10.second

  class TestActor(id: Int, replyToMessages: Boolean) extends PersistentActor with Stash {
    override val persistenceId: String = s"actor-$id"

    var state: Int = 0

    override def receiveCommand: Receive =
      LoggingReceive {
        case "state" =>
          sender() ! state

        case AccountOpenedCommand(balance) =>
          persist(AccountOpenedEvent(balance)) { (event: AccountOpenedEvent) =>
            updateState(event)
            if (replyToMessages) sender() ! akka.actor.Status.Success(event)
          }
        case GetBalanceCommand => sender() ! state
        case _ =>
          if (replyToMessages) sender() ! akka.actor.Status.Success()
      }

    def updateState(event: AccountOpenedEvent): Unit = {
      state = state + event.balance
    }

    override def receiveRecover: Receive =
      LoggingReceive { case event: AccountOpenedEvent =>
        updateState(event)
      }
  }

  def setupEmpty(persistenceId: Int, replyToMessages: Boolean)(implicit system: ActorSystem): ActorRef = {
    system.actorOf(Props(new TestActor(persistenceId, replyToMessages)))
  }

  def withTestActors(seq: Int = 1, replyToMessages: Boolean = false)(f: (ActorRef, ActorRef, ActorRef) => Unit)(
    implicit system: ActorSystem): Unit = {
    val refs = (seq until seq + 3).map(setupEmpty(_, replyToMessages)).toList
    try f(refs.head, refs.drop(1).head, refs.drop(2).head)
    finally killActors(refs: _*)
  }

  def withManyTestActors(amount: Int, seq: Int = 1, replyToMessages: Boolean = false)(f: Seq[ActorRef] => Unit)(
    implicit system: ActorSystem): Unit = {
    val refs = (seq until seq + amount).map(setupEmpty(_, replyToMessages)).toList
    try f(refs)
    finally killActors(refs: _*)
  }

  def withTags(payload: Any, tags: String*) = Tagged(payload, Set(tags: _*))






  it should "not find an event by tag for unknown tag" in  {
    pending
    withActorSystem { implicit system =>
      //val dao = new ByteArrayJournalDao(db, profile, journalConfig, SerializationExtension(system))

      val journalOps = new ScalaJdbcReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
        (actor1 ? AccountOpenedCommand(1)).futureValue
        (actor2 ? AccountOpenedCommand(2)).futureValue
        (actor3 ? AccountOpenedCommand(3)).futureValue
        (actor1 ? AccountOpenedCommand(1)).futureValue
        (actor2 ? AccountOpenedCommand(2)).futureValue
        (actor3 ? AccountOpenedCommand(3)).futureValue
        (actor1 ? AccountOpenedCommand(1)).futureValue //balance 3
        (actor2 ? AccountOpenedCommand(2)).futureValue //balance 6
        (actor3 ? AccountOpenedCommand(3)).futureValue //balance 9

        eventually {
          journalOps.countJournal.futureValue shouldBe 9
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
/*class MysqlBaseJournalMigratorSpecF
  extends BaseJournalMigratorSpecF("mysql-application.conf")
    with MysqlCleaner*/

