package akka.persistence.jdbc.extension

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.persistence.jdbc.common.PluginConfig
import scalikejdbc.{AutoSession, ConnectionPool}

object ScalikeExtension extends ExtensionId[ScalikeExtensionImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ScalikeExtensionImpl = new ScalikeExtensionImpl(system)

  override def lookup() = ScalikeExtension
}

class ScalikeExtensionImpl(system: ExtendedActorSystem) extends Extension {
  implicit val session = AutoSession
  val cfg = PluginConfig(system)
  val poolName = "akka-persistence-jdbc"

  Class.forName(cfg.driverClassName)
  ConnectionPool.singleton(cfg.url, cfg.username, cfg.password)
}
