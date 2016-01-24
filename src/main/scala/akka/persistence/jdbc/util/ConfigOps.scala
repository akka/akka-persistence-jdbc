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

package akka.persistence.jdbc.util

import com.typesafe.config.Config

import scala.util.{ Failure, Try }

object ConfigOps {
  implicit class ConfigOperations(val config: Config) extends AnyVal {
    def as[A](path: String): Try[A] =
      Try(config.getAnyRef(path)).map(_.asInstanceOf[A])
    def as[A](path: String, default: A): A = {
      Try(config.getAnyRef(path)).map(_.asInstanceOf[A]).recoverWith {
        case t: Throwable ⇒
          println(t.getMessage)
          Failure(t)
      }.getOrElse(default)
    }

    def ?[A](path: String): Try[A] = as(path)
    def ?:[A](path: String, default: A) = as(path, default)
    def withPath[A](path: String)(f: Config ⇒ A): A = f(config.getConfig(path))
  }

  implicit def TryToOption[A](t: Try[A]): Option[A] = t.toOption

  implicit class TryOps[A](val t: Try[A]) extends AnyVal {
    def ?:(default: A): A = t.getOrElse(default)
  }
}
