/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.util

import akka.ConfigurationException

import java.util.Locale
import java.util.concurrent.TimeUnit
import com.typesafe.config.{ Config, ConfigFactory }

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.language.implicitConversions
import scala.util.Try

object ConfigOps {
  implicit class ConfigOperations(val config: Config) extends AnyVal {
    def as[A](key: String): Try[A] =
      Try(config.getAnyRef(key)).map(_.asInstanceOf[A])

    def as[A](key: String, default: A): A =
      Try(config.getAnyRef(key)).map(_.asInstanceOf[A]).getOrElse(noDefault(key))

    def asConfig(key: String, default: Config = ConfigFactory.empty) =
      Try(config.getConfig(key)).getOrElse(noDefault(key))

    def asInt(key: String, default: Int): Int =
      Try(config.getInt(key)).getOrElse(noDefault(key))

    def asString(key: String, default: String): String =
      Try(config.getString(key)).getOrElse(noDefault(key))

    def asOptionalNonEmptyString(key: String): Option[String] = {
      if (config.hasPath(key)) Some(config.getString(key)).filterNot(_.isEmpty) else None
    }

    def asBoolean(key: String, default: Boolean) =
      Try(config.getBoolean(key)).getOrElse(noDefault(key))

    def asFiniteDuration(key: String, default: FiniteDuration) =
      Try(FiniteDuration(config.getDuration(key).toMillis, TimeUnit.MILLISECONDS)).getOrElse(default)

    def asDuration(key: String): Duration =
      config.getString(key).toLowerCase(Locale.ROOT) match {
        case "off" => Duration.Undefined
        case _     => config.getMillisDuration(key).requiring(_ > Duration.Zero, key + " >0s, or off")
      }

    def getMillisDuration(key: String): FiniteDuration = getDuration(key, TimeUnit.MILLISECONDS)

    def getNanosDuration(key: String): FiniteDuration = getDuration(key, TimeUnit.NANOSECONDS)

    def getDuration(key: String, unit: TimeUnit): FiniteDuration = Duration(config.getDuration(key, unit), unit)

    def ?[A](key: String): Try[A] = as(key)

    def ?:[A](key: String, default: A) = as(key, noDefault(key))

    def withkey[A](key: String)(f: Config => A): A = f(config.getConfig(key))
  }

  private def noDefault(key: String) = {
    throw new ConfigurationException(s"The [$key] setting relies on a default provided in code.")
  }

  implicit def TryToOption[A](t: Try[A]): Option[A] = t.toOption

  final implicit class TryOps[A](val t: Try[A]) extends AnyVal {
    def ?:(default: A): A = t.getOrElse(default)
  }

  final implicit class StringTryOps(val t: Try[String]) extends AnyVal {

    /**
     * Trim the String content, when empty, return None
     */
    def trim: Option[String] = t.map(_.trim).filter(_.nonEmpty)
  }

  final implicit class Requiring[A](val value: A) extends AnyVal {
    @inline def requiring(cond: Boolean, msg: => Any): A = {
      require(cond, msg)
      value
    }

    @inline def requiring(cond: A => Boolean, msg: => Any): A = {
      require(cond(value), msg)
      value
    }
  }
}
