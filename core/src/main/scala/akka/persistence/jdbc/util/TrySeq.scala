/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.util

import scala.annotation.nowarn
import scala.collection.immutable.*
import scala.util.{Failure, Success, Try}

object TrySeq {
  def sequence[A](seq: Seq[Try[A]]): Try[Seq[A]] = {
    @nowarn("msg=exhaustive")
    def recurse(remaining: Seq[Try[A]], processed: Seq[A]): Try[Seq[A]] = {
      remaining match {
        case Seq()                 => Success(processed)
        case Success(head) +: tail => recurse(remaining = tail, processed :+ head)
        case Failure(t) +: _       => Failure(t)
      }
    }

    recurse(seq, Vector.empty)
  }
}
