package akka.persistence.jdbc.extension

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.persistence.jdbc.common.PluginConfig
import scalikejdbc._

object ScalikeExtension extends ExtensionId[ScalikeExtensionImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ScalikeExtensionImpl = new ScalikeExtensionImpl(system)

  override def lookup() = ScalikeExtension
}

class ScalikeExtensionImpl(system: ExtendedActorSystem) extends Extension {

  GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings (
    enabled = true,
    singleLineMode = false,
    printUnprocessedStackTrace = false,
    stackTraceDepth= 15,
    logLevel = 'debug,
    warningEnabled = false,
    warningThresholdMillis = 3000L,
    warningLogLevel = 'warn
  )

  val session = AutoSession
  val cfg = PluginConfig(system)

  Class.forName(cfg.driverClassName)

  ConnectionPool.singleton (
    cfg.url,
    cfg.username,
    cfg.password,
    ConnectionPoolSettings (
      validationQuery = cfg.validationQuery,
      initialSize = 1,
      maxSize = 8,
      connectionTimeoutMillis = 5000L,
      connectionPoolFactoryName = "commons-dbcp"
    )
  )
}
