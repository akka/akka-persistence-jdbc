/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.query

import akka.persistence.query.{ EventEnvelope, NoOffset, Sequence }
import akka.pattern._
import scala.concurrent.duration._

abstract class LogicalDeleteQueryTest(config: String) extends QueryTestSpec(config) {
  implicit val askTimeout = 500.millis

  it should "return logically deleted events when using CurrentEventsByTag (backward compatibility)" in withActorSystem {
    implicit system =>
      val journalOps = new ScalaJdbcReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, _, _) =>
        (actor1 ? withTags(1, "number")).futureValue
        (actor1 ? withTags(2, "number")).futureValue
        (actor1 ? withTags(3, "number")).futureValue

        // delete and wait for confirmation
        (actor1 ? DeleteCmd(1)).futureValue

        journalOps.withCurrentEventsByTag()("number", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(Sequence(1), _, _, _) => }
          tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, _) => }
          tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
          tp.expectComplete()
        }
      }
  }

  it should "return logically deleted events when using EventsByTag (backward compatibility)" in withActorSystem {
    implicit system =>
      val journalOps = new ScalaJdbcReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, _, _) =>
        (actor1 ? withTags(1, "number")).futureValue
        (actor1 ? withTags(2, "number")).futureValue
        (actor1 ? withTags(3, "number")).futureValue

        // delete and wait for confirmation
        (actor1 ? DeleteCmd(1)).futureValue shouldBe "deleted-1"

        journalOps.withEventsByTag()("number", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(Sequence(1), _, _, _) => }
          tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, _) => }
          tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
          tp.cancel()
        }
      }
  }

  it should "return logically deleted events when using CurrentEventsByPersistenceId (backward compatibility)" in withActorSystem {
    implicit system =>
      val journalOps = new ScalaJdbcReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, _, _) =>
        (actor1 ? withTags(1, "number")).futureValue
        (actor1 ? withTags(2, "number")).futureValue
        (actor1 ? withTags(3, "number")).futureValue

        // delete and wait for confirmation
        (actor1 ? DeleteCmd(1)).futureValue shouldBe "deleted-1"

        journalOps.withCurrentEventsByPersistenceId()("my-1") { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(Sequence(1), _, _, _) => }
          tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, _) => }
          tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
          tp.expectComplete()
        }
      }
  }

  it should "return logically deleted events when using EventsByPersistenceId (backward compatibility)" in withActorSystem {
    implicit system =>
      val journalOps = new ScalaJdbcReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, _, _) =>
        (actor1 ? withTags(1, "number")).futureValue
        (actor1 ? withTags(2, "number")).futureValue
        (actor1 ? withTags(3, "number")).futureValue

        // delete and wait for confirmation
        (actor1 ? DeleteCmd(1)).futureValue shouldBe "deleted-1"

        journalOps.withEventsByPersistenceId()("my-1") { tp =>
          tp.request(Int.MaxValue)
          tp.expectNextPF { case EventEnvelope(Sequence(1), _, _, _) => }
          tp.expectNextPF { case EventEnvelope(Sequence(2), _, _, _) => }
          tp.expectNextPF { case EventEnvelope(Sequence(3), _, _, _) => }
          tp.cancel()
        }
      }
  }
}

class PostgresLogicalDeleteQueryTest extends LogicalDeleteQueryTest("postgres-application.conf") with PostgresCleaner

class MySQLLogicalDeleteQueryTest extends LogicalDeleteQueryTest("mysql-application.conf") with MysqlCleaner

class OracleLogicalDeleteQueryTest extends LogicalDeleteQueryTest("oracle-application.conf") with OracleCleaner

class SqlServerLogicalDeleteQueryTest extends LogicalDeleteQueryTest("sqlserver-application.conf") with SqlServerCleaner

class H2LogicalDeleteQueryTest extends LogicalDeleteQueryTest("h2-application.conf") with H2Cleaner
