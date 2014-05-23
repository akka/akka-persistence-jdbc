package akka.persistence.jdbc.common

import akka.actor.Actor

trait ActorConfig extends Config { this: Actor =>
  val config = context.system.settings.config.getConfig("jdbc-connection")

  def username = config.getString("username")
  def password = config.getString("password")
  def driverClassName = config.getString("driverClassName")
  def url = config.getString("url")
  def maxActive = config.getInt("maxActive")
  def maxIdle = config.getInt("maxIdle")
  def initialSize = config.getInt("initialSize")
}
