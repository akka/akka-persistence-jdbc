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

package akka.persistence.jdbc.generator

import org.scalacheck.Gen

import scala.collection.AbstractIterator

object GeneratorHelper {
  implicit class IteratorGen[T](gen: Gen[T]) extends AbstractIterator[T] {
    override def hasNext: Boolean = true

    def get(e: Option[T]): T = if (e.isDefined) e.get else get(gen.sample)

    override def next(): T = get(gen.sample)
  }
}
