package akka.persistence.h2.common

import org.h2.tools.Server

trait H2Server {

  var server: Option[Server] = None

  def start(port: String) = {
    this.server = Some(Server.createTcpServer(Array("-tcpPort", port, "-tcpAllowOthers"): _*))
  }

  def stop() = {
    server map { server =>
      server.stop()
    }
    this.server = None
  }
}
