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

import akka.persistence.jdbc.TestSpec

class TagsSerializationTest extends TestSpec {

  "Encode" should "no tags" in {
    encodeTags(Set.empty[String], ",") shouldBe None
  }

  it should "one tag" in {
    encodeTags(Set("foo"), ",").value shouldBe "foo"
  }

  it should "two tags" in {
    encodeTags(Set("foo", "bar"), ",").value shouldBe "foo,bar"
  }

  it should "three tags" in {
    encodeTags(Set("foo", "bar", "baz"), ",").value shouldBe "foo,bar,baz"
  }

  "decode" should "no tags" in {
    decodeTags(None, ",") shouldBe Set()
  }

  it should "one tag with separator" in {
    decodeTags(Some("foo"), ",") shouldBe Set("foo")
  }

  it should "two tags with separator" in {
    decodeTags(Some("foo,bar"), ",") shouldBe Set("foo", "bar")
  }

  it should "three tags with separator" in {
    decodeTags(Some("foo,bar,baz"), ",") shouldBe Set("foo", "bar", "baz")
  }

  "TagsSerialization" should "be bijective" in {
    val tags: Set[String] = Set("foo", "bar", "baz")
    decodeTags(encodeTags(tags, ","), ",") shouldBe tags
  }
}
