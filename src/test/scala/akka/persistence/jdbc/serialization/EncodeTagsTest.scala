/*
 * Copyright 2015 Dennis Vriend
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

  it should "encode tags given a tagPrefix" in {

    withClue("no tags") {
      val tagPrefix = "$$$"
      SerializationFacade.encodeTags(Set.empty[String], tagPrefix) shouldBe None
    }

    withClue("one tag") {
      val tagPrefix = "$$$"
      SerializationFacade.encodeTags(Set("foo"), tagPrefix).value shouldBe "$$$foo$$$"
    }

    withClue("two tags") {
      val tagPrefix = "$$$"
      SerializationFacade.encodeTags(Set("foo", "bar"), tagPrefix).value shouldBe "$$$foo$$$bar$$$"
    }
  }
}
