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

import akka.NotUsed
import akka.persistence.query._
import akka.stream.scaladsl.Source
import scala.language.implicitConversions

package object query {
  implicit class OffsetOps(val that: Offset) extends AnyVal {
    def value = that match {
      case Sequence(offsetValue) => offsetValue
      case NoOffset              => 0L
      case _                     => throw new IllegalArgumentException("akka-persistence-jdbc does not support " + that.getClass.getName + " offsets")
    }
  }

  def toNewEnvelope(env: EventEnvelope): EventEnvelope2 = env match {
    case EventEnvelope(offset, persistenceId, sequenceNr, event) =>
      EventEnvelope2(Sequence(offset), persistenceId, sequenceNr, event)
  }

  implicit def oldSrcToNewSrc(that: Source[EventEnvelope, NotUsed]): Source[EventEnvelope2, NotUsed] =
    that.map(toNewEnvelope)
}
