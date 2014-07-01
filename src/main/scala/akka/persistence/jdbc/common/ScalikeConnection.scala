package akka.persistence.jdbc.common

import scalikejdbc.{AutoSession, ConnectionPool}

trait ScalikeConnection {
  implicit val session = AutoSession
  def pluginConfig: PluginConfig

  Class.forName(pluginConfig.driverClassName)
  ConnectionPool.singleton(pluginConfig.url, pluginConfig.username, pluginConfig.password)
}
