package akka.persistence.jdbc.query

import akka.persistence.query.{ EventEnvelope, NoOffset, Sequence }
import akka.pattern._
import com.typesafe.config.ConfigFactory
import org.scalactic.source.Position
import org.scalatest.Matchers

import scala.concurrent.duration._

abstract class HardDeleteQueryTest(config: String) extends QueryTestSpec(config) with Matchers {

  implicit val askTimeout = 500.millis

  it should "not return deleted events when using CurrentEventsByTag" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, _, _) =>
      (actor1 ? withTags(1, "number")).futureValue
      (actor1 ? withTags(2, "number")).futureValue
      (actor1 ? withTags(3, "number")).futureValue

      // delete and wait for confirmation
      (actor1 ? DeleteCmd(1)).futureValue shouldBe "deleted-1"

      journalOps.withCurrentEventsByTag()("number", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
        tp.expectComplete()
      }

    }
  }

  it should "not return deleted events when using EventsByTag" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, _, _) =>
      (actor1 ? withTags(1, "number")).futureValue
      (actor1 ? withTags(2, "number")).futureValue
      (actor1 ? withTags(3, "number")).futureValue

      // delete and wait for confirmation
      (actor1 ? DeleteCmd(1)).futureValue shouldBe "deleted-1"

      journalOps.withEventsByTag()("number", NoOffset) { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
        tp.cancel()
      }

    }
  }

  it should "not return deleted events when using CurrentEventsByPersistenceId" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, _, _) =>
      (actor1 ? withTags(1, "number")).futureValue
      (actor1 ? withTags(2, "number")).futureValue
      (actor1 ? withTags(3, "number")).futureValue

      // delete and wait for confirmation
      (actor1 ? DeleteCmd(1)).futureValue shouldBe "deleted-1"

      journalOps.withCurrentEventsByPersistenceId()("my-1") { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
        tp.expectComplete()
      }

    }
  }

  it should "not return deleted events when using EventsByPersistenceId" in withActorSystem { implicit system =>
    val journalOps = new ScalaJdbcReadJournalOperations(system)
    withTestActors(replyToMessages = true) { (actor1, _, _) =>
      (actor1 ? withTags(1, "number")).futureValue
      (actor1 ? withTags(2, "number")).futureValue
      (actor1 ? withTags(3, "number")).futureValue

      // delete and wait for confirmation
      (actor1 ? DeleteCmd(1)).futureValue shouldBe "deleted-1"

      journalOps.withEventsByPersistenceId()("my-1") { tp =>
        tp.request(Int.MaxValue)
        tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, _) => }
        tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
        tp.cancel()
      }

    }
  }
}

class PostgresHardDeleteQueryTest extends HardDeleteQueryTest("postgres-application-with-hard-delete.conf") with PostgresCleaner
class MySQLHardDeleteQueryTest extends HardDeleteQueryTest("mysql-application-with-hard-delete") with MysqlCleaner
class OracleHardDeleteQueryTest extends HardDeleteQueryTest("oracle-application-with-hard-delete") with OracleCleaner
class H2HardDeleteQueryTest extends HardDeleteQueryTest("h2-application-with-hard-delete") with H2Cleaner
