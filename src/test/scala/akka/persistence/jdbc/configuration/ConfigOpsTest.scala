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

package akka.persistence.jdbc.configuration

import akka.persistence.jdbc.SimpleSpec
import akka.persistence.jdbc.util.ConfigOps
import ConfigOps._
import com.typesafe.config.ConfigFactory

class ConfigOpsTest extends SimpleSpec {
  it should "parse field values to Try[A]" in {
    val cfg = ConfigFactory.parseString(
      """
        | person {
        |   firstName = "foo"
        |   lastName = "bar"
        |   age = 25
        |   hasCar = true
        |   hasGirlfriend = false
        | }
      """.stripMargin
    )

    cfg.as[String]("person.firstName").success.value shouldBe "foo"
    cfg.as[String]("person.lastName").success.value shouldBe "bar"
    cfg.as[Int]("person.age").success.value shouldBe 25
    cfg.as[Boolean]("person.hasCar").success.value shouldBe true
    cfg.as[Boolean]("person.hasGirlfriend").success.value shouldBe false
  }

  it should "parse field values with default values with empty config" in {
    val cfg = ConfigFactory.parseString("")
    cfg.as[String]("person.firstName", "john") shouldBe "john"
    cfg.as[String]("person.lastName", "doe") shouldBe "doe"
    cfg.as[Int]("person.age", 35) shouldBe 35
    cfg.as[Boolean]("person.hasCar", false) shouldBe false
    cfg.as[Boolean]("person.hasGirlfriend", false) shouldBe false
  }

  it should "parse field values with default values for wrong config" in {
    val cfg = ConfigFactory.parseString(
      """
        | RedShirt {
        |   firstName = "red"
        |   lastName = "shirt"
        |   age = 25
        |   hasSpaceship = true
        |   hasGirlfriend = true
        | }
      """.stripMargin
    )

    cfg.as[String]("person.firstName", "john") shouldBe "john"
    cfg.as[String]("person.lastName", "doe") shouldBe "doe"
    cfg.as[Int]("person.age", 35) shouldBe 35
    cfg.as[Boolean]("person.hasCar", false) shouldBe false
    cfg.as[Boolean]("person.hasGirlfriend", false) shouldBe false
  }

  it should "parse field values to with defaults" in {
    val cfg = ConfigFactory.parseString(
      """
        | person {
        |   age = 25
        |   hasGirlfriend = true
        | }
      """.stripMargin
    )

    cfg.as[String]("person.firstName", "john") shouldBe "john"
    cfg.as[String]("person.lastName", "doe") shouldBe "doe"
    cfg.as[Int]("person.age", 35) shouldBe 25
    cfg.as[Boolean]("person.hasCar", false) shouldBe false
    cfg.as[Boolean]("person.hasGirlfriend", false) shouldBe true
  }

}
