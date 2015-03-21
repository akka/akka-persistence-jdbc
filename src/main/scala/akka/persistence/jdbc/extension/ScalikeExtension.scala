package akka.persistence.jdbc.extension

import javax.naming._
import javax.sql._

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.event.Logging
import akka.persistence.jdbc.common.PluginConfig
import akka.persistence.jdbc.serialization.{JournalTypeConverter, SnapshotTypeConverter}
import scalikejdbc._

import scala.util.Try

object ScalikeExtension extends ExtensionId[ScalikeExtensionImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ScalikeExtensionImpl = new ScalikeExtensionImpl(system)

  override def lookup() = ScalikeExtension
}

class ScalikeExtensionImpl(val system: ExtendedActorSystem) extends Extension {

  val log = Logging(system, this.getClass)

  def journalConverterOf(journalConverterFQN: String): Try[JournalTypeConverter] =
    system.dynamicAccess.createInstanceFor[JournalTypeConverter](journalConverterFQN, Nil)

  def snapshotConverterOf(snapshotConverterFQN: String): Try[SnapshotTypeConverter] =
    system.dynamicAccess.createInstanceFor[SnapshotTypeConverter](snapshotConverterFQN, Nil)

  val journalConverter = journalConverterOf(system.settings.config.getString("jdbc-connection.journal-converter")).get

  val snapshotConverter = snapshotConverterOf(system.settings.config.getString("jdbc-connection.snapshot-converter")).get

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

  if(cfg.jndiPath.isDefined && cfg.dataSourceName.isDefined) {
    log.debug("Initializing datasource using JNDI data source: {}, {}", cfg.jndiPath, cfg.dataSourceName)
    val ds: DataSource = (new InitialContext).lookup(cfg.jndiPath.getOrElse("")).asInstanceOf[Context].lookup(cfg.dataSourceName.getOrElse("")).asInstanceOf[DataSource]
    ConnectionPool.singleton(new DataSourceConnectionPool(ds))
  } else {
    log.info("Initializing ScalikeJdbc using local connection pool")
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
}
