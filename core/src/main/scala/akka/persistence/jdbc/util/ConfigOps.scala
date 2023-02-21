/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.util

import com.typesafe.config.Config

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object ConfigOps {

  implicit class ConfigOperations(val config: Config) extends AnyVal {
    def asStringOption(key: String): Option[String] =
      if (config.hasPath(key)) {
        val value = config.getString(key).trim
        if (value.isEmpty) None
        else Some(value)
      } else None

    def asFiniteDuration(key: String): FiniteDuration =
      FiniteDuration(config.getDuration(key).toMillis, TimeUnit.MILLISECONDS)

  }
}
