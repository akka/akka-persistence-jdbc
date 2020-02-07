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

import akka.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[jdbc] object PluginVersionChecker {
  def check(): Unit =
    try {
      Class.forName("akka.persistence.jdbc.db.DefaultSlickDatabaseProvider")
      throw new RuntimeException(
        "Newer version of Akka Persistence JDBC found on the classpath. Remove `com.github.dnvriend:akka-persistence-jdbc` from the classpath..")
    } catch {
      case _: ClassNotFoundException =>
      // All good! That's intentional.
      // It's good if we don't have akka.persistence.jdbc.util.DefaultSlickDatabaseProvider around
    }
}
