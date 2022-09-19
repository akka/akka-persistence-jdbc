/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc

import akka.actor.{ ActorRef, ActorSystem }
import akka.persistence.jdbc.util.ClasspathResources
import akka.testkit.TestProbe
import org.scalatest._
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait SimpleSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaFutures
    with TryValues
    with OptionValues
    with Eventually
    with ClasspathResources
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with GivenWhenThen {

  /**
   * Sends the PoisonPill command to an actor and waits for it to die
   */
  def killActors(actors: ActorRef*)(implicit system: ActorSystem): Unit = {
    val tp = TestProbe()
    actors.foreach { (actor: ActorRef) =>
      tp.watch(actor)
      system.stop(actor)
      tp.expectTerminated(actor)
    }
  }
}
