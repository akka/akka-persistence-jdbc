package akka.persistence.jdbc.common

import akka.actor.{Actor, ActorSystem}

object PluginConfig {
  def apply(system: ActorSystem) = new PluginConfig(system)
}

class PluginConfig(system: ActorSystem) {
  val config = system.settings.config.getConfig("jdbc-connection")

  def username = config.getString("username")
  def password = config.getString("password")
  def driverClassName = config.getString("driverClassName")
  def url = config.getString("url")
}

trait ActorConfig { this: Actor =>
  val pluginConfig = PluginConfig(context.system)
}
