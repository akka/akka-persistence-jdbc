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

package akka.persistence.jdbc

import akka.actor.{ ActorRef, ActorSystem }
import akka.persistence.jdbc.util.ClasspathResources
import akka.testkit.TestProbe
import org.scalatest._
import org.scalatest.concurrent.{ Eventually, ScalaFutures }

trait SimpleSpec extends FlatSpec
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
      tp watch actor
      system.stop(actor)
      tp.expectTerminated(actor)
    }
  }
}
