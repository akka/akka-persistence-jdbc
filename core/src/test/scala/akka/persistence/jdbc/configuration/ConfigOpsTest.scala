/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.configuration

import akka.persistence.jdbc.SimpleSpec
import akka.persistence.jdbc.util.ConfigOps
import ConfigOps._
import com.typesafe.config.ConfigFactory

class ConfigOpsTest extends SimpleSpec {
  it should "parse field values to Options" in {
    val cfg = ConfigFactory.parseString("""
        | person {
        |   firstName = "foo"
        |   lastName = "bar"
        |   pet = ""
        |   car = " "
        | }
      """.stripMargin)

    cfg.asStringOption("person.firstName").get shouldBe "foo"
    cfg.asStringOption("person.lastName").get shouldBe "bar"
    cfg.asStringOption("person.pet") shouldBe None
    cfg.asStringOption("person.car") shouldBe None
    cfg.asStringOption("person.bike") shouldBe None
    cfg.asStringOption("person.bike") shouldBe None
  }
}
