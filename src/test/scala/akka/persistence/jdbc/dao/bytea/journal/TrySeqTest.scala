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

package akka.persistence.jdbc.dao.bytea.journal

import akka.persistence.jdbc.util.TrySeq
import akka.persistence.jdbc.{ MaterializerSpec, SimpleSpec }
import akka.stream.scaladsl.Source
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.scaladsl.TestSink

import scala.collection.immutable._
import scala.util.{ Failure, Success }

class TrySeqTest extends SimpleSpec with MaterializerSpec {
  implicit class SourceOps[A, M](src: Source[A, M]) {
    def testProbe(f: TestSubscriber.Probe[A] ⇒ Unit): Unit =
      f(src.runWith(TestSink.probe(system)))
  }

  implicit class TestProbeOps[A](tp: TestSubscriber.Probe[A]) {
    def assertNext(right: PartialFunction[Any, _]) = tp.requestNext() should matchPattern(right)
  }

  def failure(text: String) = Failure(new RuntimeException(text))

  it should "sequence an empty immutable.Seq" in {
    Source.single(Seq.empty).via(TrySeq.sequence).testProbe { tp =>
      tp.request(Long.MaxValue)
      tp.expectNext(Success(Seq.empty))
    }
  }

  it should "sequence an empty immutable.Vector" in {
    Source.single(Vector.empty).via(TrySeq.sequence).testProbe { tp =>
      tp.request(Long.MaxValue)
      tp.expectNext(Success(Seq.empty))
    }
  }

  it should "sequence a immutable.Seq of success/success" in {
    Source.single(Seq(Success("a"), Success("b"))).via(TrySeq.sequence).testProbe { tp =>
      tp.request(Long.MaxValue)
      tp.expectNext(Success(Seq("a", "b")))
    }
  }

  it should "sequence an immutable Seq of success/failure" in {
    Source.single(List(Success("a"), failure("b"))).via(TrySeq.sequence).testProbe { tp =>
      tp.request(Long.MaxValue)
      tp.assertNext { case Failure(cause) if cause.getMessage.contains("b") => }
    }
  }

  it should "sequence an immutable Seq of failure/success" in {
    Source.single(List(failure("a"), Success("b"))).via(TrySeq.sequence).testProbe { tp =>
      tp.request(Long.MaxValue)
      tp.assertNext { case Failure(cause) if cause.getMessage.contains("a") => }
    }
  }

  it should "sequence an immutable.Seq of failure/failure" in {
    Source.single(Seq(failure("a"), failure("b"))).via(TrySeq.sequence).testProbe { tp =>
      tp.request(Long.MaxValue)
      tp.assertNext { case Failure(cause) if cause.getMessage.contains("a") => }
    }
  }

  it should "sequence an immutable.Vector of failure/failure" in {
    Source.single(Vector(failure("a"), failure("b"))).via(TrySeq.sequence).testProbe { tp =>
      tp.request(Long.MaxValue)
      tp.assertNext { case Failure(cause) if cause.getMessage.contains("a") => }
    }
  }
}
