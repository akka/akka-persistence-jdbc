/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc

import akka.NotUsed
import akka.persistence.query._
import akka.stream.scaladsl.Source
import scala.language.implicitConversions

package object query {
  implicit class OffsetOps(val that: Offset) extends AnyVal {
    def value =
      that match {
        case Sequence(offsetValue) => offsetValue
        case NoOffset              => 0L
        case _ =>
          throw new IllegalArgumentException(
            "akka-persistence-jdbc does not support " + that.getClass.getName + " offsets")
      }
  }
}
