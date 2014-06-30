package akka.persistence.jdbc.common

import akka.actor.{Actor, ActorSystem}

object Config {
  def apply(system: ActorSystem) = new Config(system)
}

class Config(system: ActorSystem) {
  val config = system.settings.config.getConfig("jdbc-connection")

  def username = config.getString("username")
  def password = config.getString("password")
  def driverClassName = config.getString("driverClassName")
  def url = config.getString("url")
}

trait ActorConfig { this: Actor =>
  val config = Config(context.system)
}
