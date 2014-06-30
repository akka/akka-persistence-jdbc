package akka.persistence.jdbc.common

import scalikejdbc.{AutoSession, ConnectionPool}

trait ScalikeConnection {
  implicit val session = AutoSession
  def config: Config

  Class.forName(config.driverClassName)
  ConnectionPool.singleton(config.url, config.username, config.password)
}
