/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

///*
// * Copyright 2016 Dennis Vriend
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package akka.persistence.jdbc.query
//
//import akka.persistence.jdbc.util.Schema.{ MySQL, Oracle, Postgres }
//import akka.persistence.query.EventEnvelope
//
//import scala.concurrent.duration._
//
//abstract class EventsByPersistenceIdAndTagTest(config: String) extends QueryTestSpec(config) {
//  it should "not find events for unknown tags" in {
//    withTestActors() { (actor1, actor2, actor3) ⇒
//      actor1 ! withTags(1, "one")
//      actor2 ! withTags(2, "two")
//      actor3 ! withTags(3, "three")
//
//      eventually {
//        journalDao.countJournal.futureValue shouldBe 3
//      }
//
//      withEventsByPersistenceIdAndTag()("my-1", "unknown", 0) { tp ⇒
//        tp.request(Int.MaxValue)
//        tp.expectNoMsg(100.millis)
//        tp.cancel()
//        tp.expectNoMsg(100.millis)
//      }
//    }
//  }
//
//  it should "find all events by tag" in {
//    withTestActors() { (actor1, actor2, actor3) ⇒
//      actor1 ! withTags(1, "number")
//      actor2 ! withTags(2, "number")
//      actor3 ! withTags(3, "number")
//
//      eventually {
//        journalDao.countJournal.futureValue shouldBe 3
//      }
//
//      withEventsByPersistenceIdAndTag()("my-1", "number", 0) { tp ⇒
//        tp.request(Int.MaxValue)
//        tp.expectNextPF { case EventEnvelope(1, "my-1", 1, _) ⇒ }
//        tp.expectNoMsg(100.millis)
//
//        actor1 ! withTags(4, "number")
//        tp.expectNextPF { case EventEnvelope(2, "my-1", 2, _) ⇒ }
//        tp.cancel()
//        tp.expectNoMsg(100.millis)
//      }
//    }
//  }
//
//  it should "find events by tag from an offset" in {
//    withTestActors() { (actor1, actor2, actor3) ⇒
//      actor1 ! withTags(1, "number")
//      actor2 ! withTags(2, "number")
//      actor3 ! withTags(3, "number")
//
//      eventually {
//        journalDao.countJournal.futureValue shouldBe 3
//      }
//
//      withEventsByPersistenceIdAndTag()("my-1", "number", 2) { tp ⇒
//        tp.request(Int.MaxValue)
//        tp.expectNoMsg(100.millis)
//
//        actor1 ! withTags(4, "number")
//        tp.expectNextPF { case EventEnvelope(3, _, _, _) ⇒ }
//        tp.cancel()
//        tp.expectNoMsg(100.millis)
//      }
//    }
//  }
//
//  it should "persist and find tagged event for one tag" in {
//    withTestActors() { (actor1, actor2, actor3) ⇒
//      withEventsByPersistenceIdAndTag()("my-1", "one", 0) { tp ⇒
//        tp.request(Int.MaxValue)
//        tp.expectNoMsg(100.millis)
//
//        actor1 ! withTags(1, "one")
//        tp.expectNextPF { case EventEnvelope(1, "my-1", 1, _) ⇒ }
//        tp.expectNoMsg(100.millis)
//
//        actor2 ! withTags(1, "one")
//        tp.expectNoMsg(100.millis)
//
//        actor3 ! withTags(1, "one")
//        tp.expectNoMsg(100.millis)
//
//        actor1 ! withTags(2, "two")
//        tp.expectNoMsg(100.millis)
//        actor2 ! withTags(2, "two")
//        tp.expectNoMsg(100.millis)
//        actor3 ! withTags(2, "two")
//        tp.expectNoMsg(100.millis)
//
//        actor1 ! withTags(3, "one")
//        tp.expectNextPF { case EventEnvelope(2, "my-1", 3, _) ⇒ }
//        tp.expectNoMsg(100.millis)
//
//        actor2 ! withTags(3, "one")
//        tp.expectNoMsg(100.millis)
//
//        actor3 ! withTags(3, "one")
//        tp.expectNoMsg(100.millis)
//
//        tp.cancel()
//        tp.expectNoMsg(100.millis)
//      }
//    }
//  }
//
//  it should "persist and find tagged events when stored with multiple tags" in {
//    withTestActors() { (actor1, actor2, actor3) ⇒
//      actor1 ! withTags(1, "one", "1", "prime")
//      actor1 ! withTags(2, "two", "2", "prime")
//      actor1 ! withTags(3, "three", "3", "prime")
//      actor1 ! withTags(4, "four", "4")
//      actor1 ! withTags(5, "five", "5", "prime")
//      actor2 ! withTags(3, "three", "3", "prime")
//      actor3 ! withTags(3, "three", "3", "prime")
//
//      actor1 ! 6
//      actor1 ! 7
//      actor1 ! 8
//      actor1 ! 9
//      actor1 ! 10
//
//      eventually {
//        journalDao.countJournal.futureValue shouldBe 12
//      }
//
//      withEventsByPersistenceIdAndTag()("my-1", "prime", 0) { tp ⇒
//        tp.request(Int.MaxValue)
//        tp.expectNextPF { case EventEnvelope(1, "my-1", 1, _) ⇒ }
//        tp.expectNextPF { case EventEnvelope(2, "my-1", 2, _) ⇒ }
//        tp.expectNextPF { case EventEnvelope(3, "my-1", 3, _) ⇒ }
//        tp.expectNextPF { case EventEnvelope(4, "my-1", 5, _) ⇒ }
//        tp.expectNoMsg(100.millis)
//        tp.cancel()
//      }
//
//      withEventsByPersistenceIdAndTag()("my-1", "three", 0) { tp ⇒
//        tp.request(Int.MaxValue)
//        tp.expectNextPF { case EventEnvelope(1, "my-1", 3, _) ⇒ }
//        tp.expectNoMsg(100.millis)
//        tp.cancel()
//      }
//
//      withEventsByPersistenceIdAndTag()("my-1", "3", 0) { tp ⇒
//        tp.request(Int.MaxValue)
//        tp.expectNextPF { case EventEnvelope(1, "my-1", 3, _) ⇒ }
//        tp.expectNoMsg(100.millis)
//        tp.cancel()
//      }
//
//      withEventsByPersistenceIdAndTag()("my-1", "one", 0) { tp ⇒
//        tp.request(Int.MaxValue)
//        tp.expectNextPF { case EventEnvelope(1, "my-1", 1, _) ⇒ }
//        tp.expectNoMsg(100.millis)
//        tp.cancel()
//      }
//
//      withEventsByPersistenceId()("my-1", 0, Int.MaxValue) { tp ⇒
//        tp.request(Int.MaxValue)
//        tp.expectNextPF { case EventEnvelope(1, "my-1", 1, _) ⇒ }
//        tp.expectNextPF { case EventEnvelope(2, "my-1", 2, _) ⇒ }
//        tp.expectNextPF { case EventEnvelope(3, "my-1", 3, _) ⇒ }
//        tp.expectNextPF { case EventEnvelope(4, "my-1", 4, _) ⇒ }
//        tp.expectNextPF { case EventEnvelope(5, "my-1", 5, _) ⇒ }
//        tp.expectNextPF { case EventEnvelope(6, "my-1", 6, _) ⇒ }
//        tp.expectNextPF { case EventEnvelope(7, "my-1", 7, _) ⇒ }
//        tp.expectNextPF { case EventEnvelope(8, "my-1", 8, _) ⇒ }
//        tp.expectNextPF { case EventEnvelope(9, "my-1", 9, _) ⇒ }
//        tp.expectNextPF { case EventEnvelope(10, "my-1", 10, _) ⇒ }
//        tp.expectNoMsg(100.millis)
//        tp.cancel()
//      }
//    }
//  }
//}
//
//class PostgresScalaEventsByPersistenceIdAndTagTest extends EventsByPersistenceIdAndTagTest("postgres-application.conf") with ScalaJdbcReadJournalOperations {
//  dropCreate(Postgres())
//}
//
//class MySQLScalaEventsByPersistenceIdAndTagTest extends EventsByPersistenceIdAndTagTest("mysql-application.conf") with ScalaJdbcReadJournalOperations {
//  dropCreate(MySQL())
//}
//
//class OracleScalaEventsByPersistenceIdAndTagTest extends EventsByPersistenceIdAndTagTest("oracle-application.conf") with ScalaJdbcReadJournalOperations {
//  dropCreate(Oracle())
//
//  protected override def beforeEach(): Unit =
//    clearOracle()
//
//  override protected def afterAll(): Unit =
//    clearOracle()
//}
//
//class InMemoryScalaEventsByPersistenceIdAndTagTest extends EventsByPersistenceIdAndTagTest("in-memory-application.conf") with ScalaJdbcReadJournalOperations
//
//class PostgresJavaEventsByPersistenceIdAndTagTest extends EventsByPersistenceIdAndTagTest("postgres-application.conf") with JavaDslJdbcReadJournalOperations {
//  dropCreate(Postgres())
//}
//
//class MySQLJavaEventsByPersistenceIdAndTagTest extends EventsByPersistenceIdAndTagTest("mysql-application.conf") with JavaDslJdbcReadJournalOperations {
//  dropCreate(MySQL())
//}
//
//class OracleJavaEventsByPersistenceIdAndTagTest extends EventsByPersistenceIdAndTagTest("oracle-application.conf") with JavaDslJdbcReadJournalOperations {
//  dropCreate(Oracle())
//
//  protected override def beforeEach(): Unit =
//    clearOracle()
//
//  override protected def afterAll(): Unit =
//    clearOracle()
//}
//
//class InMemoryJavaEventsByPersistenceIdAndTagTest extends EventsByPersistenceIdAndTagTest("in-memory-application.conf") with JavaDslJdbcReadJournalOperations
