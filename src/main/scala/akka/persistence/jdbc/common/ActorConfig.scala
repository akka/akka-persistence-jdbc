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

package akka.persistence.jdbc.common

import akka.actor.{ Actor, ActorSystem }

object OptionString {
  def apply(s: String) = new OptionString(s)
  implicit def stringToOptionString(s: String): OptionString = new OptionString(s)
}

class OptionString(s: String) {
  def toOption: Option[String] = if (!Option(s).getOrElse("").isEmpty) Some(s) else None
}

object PluginConfig {
  def apply(system: ActorSystem) = new PluginConfig(system)
}

class PluginConfig(system: ActorSystem) {
  import OptionString._
  val config = system.settings.config.getConfig("jdbc-connection")

  def username = config.getString("username")
  def password = config.getString("password")
  def driverClassName = config.getString("driverClassName")
  def url = config.getString("url")
  def journalSchemaName: String = config.getString("journalSchemaName").toOption.map(_ + ".").getOrElse("")
  def journalTableName = config.getString("journalTableName")
  def snapshotSchemaName: String = config.getString("snapshotSchemaName").toOption.map(_ + ".").getOrElse("")
  def snapshotTableName = config.getString("snapshotTableName")
  def validationQuery = if (config.hasPath("validationQuery")) config.getString("validationQuery") else null
  def jndiPath = config.getString("jndiPath").toOption
  def dataSourceName = config.getString("dataSourceName").toOption
}

trait ActorConfig { this: Actor ⇒
  val cfg = PluginConfig(context.system)
}
