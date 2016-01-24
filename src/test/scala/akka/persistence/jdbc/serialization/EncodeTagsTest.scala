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

class EncodeTagsTest extends TestSpec {

  "Encode" should "no tags" in {
    SerializationFacade.encodeTags(Set.empty[String], ",") shouldBe None
  }

  it should "one tag" in {
    SerializationFacade.encodeTags(Set("foo"), ",").value shouldBe "foo"
  }

  it should "two tags" in {
    SerializationFacade.encodeTags(Set("foo", "bar"), ",").value shouldBe "foo,bar"
  }

  it should "three tags" in {
    SerializationFacade.encodeTags(Set("foo", "bar", "baz"), ",").value shouldBe "foo,bar,baz"
  }

  "decode" should "one tag with separator" in {
    SerializationFacade.decodeTags("foo", ",") shouldBe List("foo")
  }

  it should "two tags with separator" in {
    SerializationFacade.decodeTags("foo,bar", ",") shouldBe List("foo", "bar")
  }

  it should "three tags with separator" in {
    SerializationFacade.decodeTags("foo,bar,baz", ",") shouldBe List("foo", "bar", "baz")
  }
}
