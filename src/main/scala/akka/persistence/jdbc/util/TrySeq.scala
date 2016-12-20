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

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}

import scala.collection.immutable._
import scala.util.{Failure, Success, Try}

object TrySeq {
  def sequence[A]: Flow[Iterable[Try[A]], Try[Seq[A]], NotUsed] =
    Flow[Iterable[Try[A]]].flatMapConcat { xs =>
      Source(xs).fold[Try[Seq[A]]](Success(Seq.empty)) {
        case (Success(ys), Success(y))     => Success(ys :+ y)
        case (Success(ys), Failure(cause)) => Failure(cause)
        case (err @ Failure(_), _)         => err
      }
    }
}
