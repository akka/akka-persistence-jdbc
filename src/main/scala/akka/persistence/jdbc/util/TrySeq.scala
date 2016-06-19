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

import scala.util.{ Failure, Success, Try }

object TrySeq {
  def sequence[R](seq: Seq[Try[R]]): Try[Seq[R]] = {
    seq match {
      case Success(h) :: tail ⇒
        tail.foldLeft(Try(h :: Nil)) {
          case (Success(acc), Success(elem)) ⇒ Success(elem :: acc)
          case (e: Failure[_], _)            ⇒ e
          case (_, Failure(e))               ⇒ Failure(e)
        }
      case Failure(e) :: _ ⇒ Failure(e)
      case Nil             ⇒ Try { Nil }
    }
  }
}
