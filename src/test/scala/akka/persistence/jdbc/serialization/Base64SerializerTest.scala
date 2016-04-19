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

import akka.persistence.jdbc.TestSpec
import org.scalatest.TryValues

import scala.util.Try

class Base64SerializerTest extends TestSpec("postgres-varchar-application.conf") with TryValues {

  val base64Serializer = serialization.serializerByIdentity(1000)

  it should "serialize a String successfully" in {
    Try(base64Serializer.toBinary("foo")) should be a 'success
  }

  it should "serialize a string as bytes successfully" in {
    Try(base64Serializer.toBinary("foo".getBytes("UTF-8"))) should be a 'success
  }

  it should "serialize and deserialize a String" in {
    val base64Encoded: Array[Byte] = base64Serializer.toBinary("foo")
    val decoded = base64Serializer.fromBinary(base64Encoded).asInstanceOf[Array[Byte]]
    new String(decoded) shouldBe "foo"
  }

  it should "serialize and deserialize a byte array" in {
    val base64Encoded: Array[Byte] = base64Serializer.toBinary("foo".getBytes("UTF-8"))
    val decoded = base64Serializer.fromBinary(base64Encoded).asInstanceOf[Array[Byte]]
    new String(decoded) shouldBe "foo"
  }
}
