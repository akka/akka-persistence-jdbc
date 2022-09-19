/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2022 Lightbend Inc. <https://www.lightbend.com>
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
