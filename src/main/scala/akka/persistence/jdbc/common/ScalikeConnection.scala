package akka.persistence.jdbc.common

import scalikejdbc.{AutoSession, ConnectionPool}

trait ScalikeConnection {
  implicit val session = AutoSession
  def cfg: PluginConfig

  Class.forName(cfg.driverClassName)
  ConnectionPool.singleton(cfg.url, cfg.username, cfg.password)
}
