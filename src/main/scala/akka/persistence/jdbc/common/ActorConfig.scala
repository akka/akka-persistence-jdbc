package akka.persistence.jdbc.common

import akka.actor.{Actor, ActorSystem}

object OptionString {
  def apply(s: String) = new OptionString(s)
  implicit def stringToOptionString(s: String) = new OptionString(s)
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
}

trait ActorConfig { this: Actor =>
  val cfg = PluginConfig(context.system)
}
