/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.journal.dao.legacy

import akka.persistence.jdbc.SharedActorSystemTestSpec

class TagsSerializationTest extends SharedActorSystemTestSpec {
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
