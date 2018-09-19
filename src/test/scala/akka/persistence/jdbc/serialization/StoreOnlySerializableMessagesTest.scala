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

package akka.persistence.jdbc.serialization

import akka.actor.{ ActorRef, Props }
import akka.event.LoggingReceive
import akka.persistence.jdbc.SharedActorSystemTestSpec
import akka.persistence.jdbc.util.Schema._
import akka.persistence.{ PersistentActor, RecoveryCompleted }
import akka.testkit.TestProbe

import scala.concurrent.duration._

abstract class StoreOnlySerializableMessagesTest(config: String, schemaType: SchemaType) extends SharedActorSystemTestSpec(config) {

  case class PersistFailure(cause: Throwable, event: Any, seqNr: Long)
  case class PersistRejected(cause: Throwable, event: Any, seqNr: Long)

  class TestActor(val persistenceId: String, recoverProbe: ActorRef, persistFailureProbe: ActorRef, persistRejectedProbe: ActorRef) extends PersistentActor {
    override val receiveRecover: Receive = LoggingReceive {
      case msg => recoverProbe ! msg
    }

    override val receiveCommand: Receive = LoggingReceive {
      case msg => persist(msg) { _ =>
        sender ! akka.actor.Status.Success("")
      }
    }

    override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit =
      persistFailureProbe ! PersistFailure(cause, event, seqNr)

    override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit =
      persistRejectedProbe ! PersistRejected(cause, event, seqNr)
  }

  def withActor(id: String = "1")(f: ActorRef => TestProbe => TestProbe => TestProbe => Unit): Unit = {
    val recoverProbe = TestProbe()
    val persistFailureProbe = TestProbe()
    val persistRejectedProbe = TestProbe()
    val persistentActor = system.actorOf(Props(new TestActor(s"my-$id", recoverProbe.ref, persistFailureProbe.ref, persistRejectedProbe.ref)))
    try f(persistentActor)(recoverProbe)(persistFailureProbe)(persistRejectedProbe) finally killActors(persistentActor)
  }

  override def beforeAll(): Unit = {
    dropCreate(schemaType)
    super.beforeAll()
  }

  it should "persist a single serializable message" in {
    withActor("1") { actor => recover => failure => rejected =>
      val tp = TestProbe()
      recover.expectMsg(RecoveryCompleted)
      tp.send(actor, "foo") // strings are serializable
      tp.expectMsg(akka.actor.Status.Success(""))
      failure.expectNoMessage(100.millis)
      rejected.expectNoMessage(100.millis)
    }

    // the recover cycle
    withActor("1") { actor => recover => failure => rejected =>
      recover.expectMsg("foo")
      recover.expectMsg(RecoveryCompleted)
      failure.expectNoMessage(100.millis)
      rejected.expectNoMessage(100.millis)
    }
  }

  it should "not persist a single non-serializable message" in {
    class NotSerializable
    withActor("2") { actor => recover => failure => rejected =>
      val tp = TestProbe()
      recover.expectMsg(RecoveryCompleted)
      tp.send(actor, new NotSerializable) // the NotSerializable class cannot be serialized
      tp.expectNoMessage(300.millis) // the handler should not have been called, because persist has failed
      // the actor should call the OnPersistRejected
      rejected.expectMsgPF() {
        case PersistRejected(_, _, _) =>
      }
    }

    // the recover cycle, no message should be recovered
    withActor("2") { actor => recover => failure => rejected =>
      recover.expectMsg(RecoveryCompleted)
      recover.expectNoMessage(100.millis)
    }
  }

  it should "persist only serializable messages" in {
    class NotSerializable
    withActor("3") { actor => recover => failure => rejected =>
      val tp = TestProbe()
      recover.expectMsg(RecoveryCompleted)
      tp.send(actor, "foo")
      tp.expectMsg(akka.actor.Status.Success(""))
      tp.send(actor, new NotSerializable) // the NotSerializable class cannot be serialized
      tp.expectNoMessage(300.millis) // the handler should not have been called, because persist has failed
      // the actor should call the OnPersistRejected
      rejected.expectMsgPF() {
        case PersistRejected(_, _, _) =>
      }
      rejected.expectNoMessage(100.millis)
    }

    // recover cycle
    withActor("3") { actor => recover => failure => rejected =>
      recover.expectMsg("foo")
      recover.expectMsg(RecoveryCompleted)
      failure.expectNoMessage(100.millis)
      rejected.expectNoMessage(100.millis)
    }
  }
}

class PostgresStoreOnlySerializableMessagesTest extends StoreOnlySerializableMessagesTest("postgres-application.conf", Postgres())

class MySQLStoreOnlySerializableMessagesTest extends StoreOnlySerializableMessagesTest("mysql-application.conf", MySQL())

class OracleStoreOnlySerializableMessagesTest extends StoreOnlySerializableMessagesTest("oracle-application.conf", Oracle())

class SqlServerStoreOnlySerializableMessagesTest extends StoreOnlySerializableMessagesTest("sqlserver-application.conf", SqlServer())

class H2StoreOnlySerializableMessagesTest extends StoreOnlySerializableMessagesTest("h2-application.conf", H2())