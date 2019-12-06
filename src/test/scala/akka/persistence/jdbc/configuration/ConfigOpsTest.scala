/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.configuration

import akka.persistence.jdbc.SimpleSpec
import akka.persistence.jdbc.util.ConfigOps
import ConfigOps._
import com.typesafe.config.ConfigFactory

class ConfigOpsTest extends SimpleSpec {
  it should "parse field values to Try[A]" in {
    val cfg = ConfigFactory.parseString("""
        | person {
        |   firstName = "foo"
        |   lastName = "bar"
        |   age = 25
        |   hasCar = true
        |   hasGirlfriend = false
        | }
      """.stripMargin)

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
    val cfg = ConfigFactory.parseString("""
        | RedShirt {
        |   firstName = "red"
        |   lastName = "shirt"
        |   age = 25
        |   hasSpaceship = true
        |   hasGirlfriend = true
        | }
      """.stripMargin)

    cfg.as[String]("person.firstName", "john") shouldBe "john"
    cfg.as[String]("person.lastName", "doe") shouldBe "doe"
    cfg.as[Int]("person.age", 35) shouldBe 35
    cfg.as[Boolean]("person.hasCar", false) shouldBe false
    cfg.as[Boolean]("person.hasGirlfriend", false) shouldBe false
  }

  it should "parse field values to with defaults" in {
    val cfg = ConfigFactory.parseString("""
        | person {
        |   age = 25
        |   hasGirlfriend = true
        | }
      """.stripMargin)

    cfg.as[String]("person.firstName", "john") shouldBe "john"
    cfg.as[String]("person.lastName", "doe") shouldBe "doe"
    cfg.as[Int]("person.age", 35) shouldBe 25
    cfg.as[Boolean]("person.hasCar", false) shouldBe false
    cfg.as[Boolean]("person.hasGirlfriend", false) shouldBe true
  }
}
