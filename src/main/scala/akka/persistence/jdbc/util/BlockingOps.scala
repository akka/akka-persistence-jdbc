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

package akka.persistence.jdbc.util

import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.concurrent.{ Await, Future }

object BlockingOps {
  implicit class BlockingFutureImplicits[T](val that: Future[T]) extends AnyVal {
    def futureValue(implicit awaitDuration: FiniteDuration = 24.hour): T =
      Await.result(that, awaitDuration)
    def printFutureValue(implicit awaitDuration: FiniteDuration = 24.hour): Unit =
      println(that.futureValue)
  }
}
