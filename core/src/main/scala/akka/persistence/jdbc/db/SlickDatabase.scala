/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.db

import akka.actor.ActorSystem
import javax.naming.InitialContext
import akka.persistence.jdbc.config.SlickConfiguration
import com.typesafe.config.Config
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcBackend._

/**
 * INTERNAL API
 */
@deprecated(message = "Internal API, will be removed in 4.0.0", since = "3.4.0")
object SlickDriver {

  /**
   * INTERNAL API
   */
  @deprecated(message = "Internal API, will be removed in 4.0.0", since = "3.4.0")
  def forDriverName(config: Config): JdbcProfile =
    SlickDatabase.profile(config, "slick")
}

/**
 * INTERNAL API
 */
object SlickDatabase {

  /**
   * INTERNAL API
   */
  @deprecated(message = "Internal API, will be removed in 4.0.0", since = "3.4.0")
  def forConfig(config: Config, slickConfiguration: SlickConfiguration): Database = {
    database(config, slickConfiguration, "slick.db")
  }

  /**
   * INTERNAL API
   */
  private[jdbc] def profile(config: Config, path: String): JdbcProfile =
    DatabaseConfig.forConfig[JdbcProfile](path, config).profile

  /**
   * INTERNAL API
   */
  private[jdbc] def database(config: Config, slickConfiguration: SlickConfiguration, path: String): Database = {
    slickConfiguration.jndiName
      .map(Database.forName(_, None))
      .orElse {
        slickConfiguration.jndiDbName.map(new InitialContext().lookup(_).asInstanceOf[Database])
      }
      .getOrElse(Database.forConfig(path, config))
  }

  /**
   * INTERNAL API
   */
  private[jdbc] def initializeEagerly(
      config: Config,
      slickConfiguration: SlickConfiguration,
      path: String): SlickDatabase = {
    val dbPath = if (path.isEmpty) "db" else s"$path.db"
    EagerSlickDatabase(database(config, slickConfiguration, dbPath), profile(config, path))
  }
}

trait SlickDatabase {
  def database: Database
  def profile: JdbcProfile

  /**
   * If true, the requesting side usually a (read/write/snapshot journal)
   * should shutdown the database when it closes. If false, it should leave
   * the database connection pool open, since it might still be used elsewhere.
   */
  def allowShutdown: Boolean
}

case class EagerSlickDatabase(database: Database, profile: JdbcProfile) extends SlickDatabase {
  override def allowShutdown: Boolean = true
}

/**
 * A LazySlickDatabase lazily initializes a database, it also manages the shutdown of the database
 * @param config The configuration used to create the database
 */
class LazySlickDatabase(config: Config, system: ActorSystem) extends SlickDatabase {
  val profile: JdbcProfile = SlickDatabase.profile(config, path = "")

  lazy val database: Database = {
    val db = SlickDatabase.database(config, new SlickConfiguration(config), path = "db")
    system.registerOnTermination {
      db.close()
    }
    db
  }

  /** This database shutdown is managed by the db holder, so users of this db do not need to bother shutting it down */
  override def allowShutdown: Boolean = false
}
