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

package akka.persistence.jdbc.serialization

import scala.util.{ Failure, Success, Try }

object MockSerializationProxy {
  /**
   * Factory method; supply an object that will be returned when
   * the proxy mocks deserialization
   */
  def apply[A](obj: A, fail: Boolean = false) = new MockSerializationProxy(obj, fail)
}

class MockSerializationProxy[A](obj: A, fail: Boolean = false) extends SerializationProxy {
  override def serialize(o: AnyRef): Try[Array[Byte]] =
    if (fail) Failure(new RuntimeException("Mock could not serialize")) else Success(Array.empty[Byte])

  override def deserialize[A](bytes: Array[Byte], clazz: Class[A]): Try[A] =
    if (fail) Failure(new RuntimeException("Mock could not deserialize"))
    else Success[A](obj.asInstanceOf[A])
}
