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

package akka.persistence.jdbc.journal.dao

import akka.persistence.jdbc.util.TrySeq
import akka.persistence.jdbc.{MaterializerSpec, SimpleSpec}

import scala.collection.immutable._
import scala.util.{Failure, Success}

class TrySeqTest extends SimpleSpec with MaterializerSpec {

  def failure(text: String) = Failure(new RuntimeException(text))

  it should "sequence an empty immutable.Seq" in {
    TrySeq.sequence(Seq.empty) shouldBe Success(Seq.empty)
  }

  it should "sequence an empty immutable.Vector" in {
    TrySeq.sequence(Vector.empty) shouldBe Success(Seq.empty)
  }

  it should "sequence a immutable.Seq of success/success" in {
    TrySeq.sequence(Seq(Success("a"), Success("b"))) shouldBe Success(Seq("a", "b"))
  }

  it should "sequence an immutable Seq of success/failure" in {
    val result = TrySeq.sequence(List(Success("a"), failure("b")))
    result should matchPattern { case Failure(cause) if cause.getMessage.contains("b") => }
  }

  it should "sequence an immutable Seq of failure/success" in {
    val result = TrySeq.sequence(List(failure("a"), Success("b")))
    result should matchPattern { case Failure(cause) if cause.getMessage.contains("a") => }
  }

  it should "sequence an immutable.Seq of failure/failure" in {
    val result = TrySeq.sequence(Seq(failure("a"), failure("b")))
    result should matchPattern { case Failure(cause) if cause.getMessage.contains("a") => }
  }

  it should "sequence an immutable.Vector of failure/failure" in {
    val result = TrySeq.sequence(Vector(failure("a"), failure("b")))
    result should matchPattern { case Failure(cause) if cause.getMessage.contains("a") => }
  }
}
