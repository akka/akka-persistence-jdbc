/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.jdbc.util

import akka.actor.{ ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import akka.persistence.jdbc.config.{ ConfigKeys, SlickConfiguration }
import akka.persistence.jdbc.util.ConfigOps._
import com.typesafe.config.{ Config, ConfigObject }
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.JdbcProfile

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

object SlickExtension extends ExtensionId[SlickExtensionImpl] with ExtensionIdProvider {
  override def lookup: SlickExtension.type = SlickExtension
  override def createExtension(system: ExtendedActorSystem) = new SlickExtensionImpl(system)
}

class SlickExtensionImpl(system: ExtendedActorSystem) extends Extension {

  private val dbProvider: SlickDatabaseProvider = {
    val fqcn = system.settings.config.getString("akka-persistence-jdbc.database-provider-fqcn")
    val args = List(classOf[ActorSystem] -> system)
    system.dynamicAccess.createInstanceFor[SlickDatabaseProvider](fqcn, args) match {
      case Success(result) => result
      case Failure(t)      => throw new RuntimeException("Failed to create SlickDatabaseProvider", t)
    }
  }

  def database(config: Config): Database = dbProvider.database(config)
  def profile(config: Config): JdbcProfile = dbProvider.profile(config)
}

/**
 * User overridable database provider.
 * Since this provider is called from an akka extension it must be thread safe!
 *
 * A SlickDatabaseProvider is loaded using reflection,
 * The instance is created using the following:
 * - The fully qualified class name as configured in `jdbc-journal.database-provider-fqcn`.
 * - The constructor with one argument of type [[akka.actor.ActorSystem]] is used to create the instance.
 *   Therefore the class must have such a constructor.
 */
trait SlickDatabaseProvider {
  /**
   * Create or retrieve the database
   * @param config The configuration which may be used to create the database. If the database is shared
   *               then this parameter can be ignored.
   */
  def database(config: Config): Database
  /**
   * Create or retrieve the profile
   * @param config The configuration which may be used to create the profile. If the database/profile is shared
   *               then this parameter can be ignored.
   */
  def profile(config: Config): JdbcProfile
}

class DefaultSlickDatabaseProvider(system: ActorSystem) extends SlickDatabaseProvider {

  val addShutDownHook: Boolean = system.settings.config.getBoolean("akka-persistence-jdbc.shared-db-add-shutdown-hook")

  val sharedDatabases: Map[String, DbHolder] = system.settings.config.getObject("akka-persistence-jdbc.shared-databases").asScala.flatMap {
    case (key, confObj: ConfigObject) =>
      val conf = confObj.toConfig
      if (conf.hasPath("profile")) {
        // Only create the DbHolder if a profile has actually been configured, this ensures that the example in the reference conf is ignored
        Some(key -> new DbHolder(conf, addShutDownHook, system))
      } else None
    case (key, notAnObject) => throw new RuntimeException(s"""Expected "akka-persistence-jdbc.shared-databases.$key" to be a config ConfigObject, but got ${notAnObject.valueType()} (${notAnObject.getClass})""")
  }.toMap

  private def getDbHolderOrThrow(sharedDbName: String): DbHolder =
    sharedDatabases.getOrElse(
      sharedDbName,
      throw new RuntimeException(s"No shared database is configured under akka-persistence-jdbc.shared-databases.$sharedDbName"))

  def database(config: Config): Database = {
    config.asOptionalNonEmptyString(ConfigKeys.useSharedDb) match {
      case None => SlickDatabase.forConfig(config, new SlickConfiguration(config), "slick.db")
      case Some(sharedDbName) =>
        getDbHolderOrThrow(sharedDbName).db
    }
  }

  def profile(config: Config): JdbcProfile = {
    config.asOptionalNonEmptyString(ConfigKeys.useSharedDb) match {
      case None => SlickDriver.forDriverName(config, "slick")
      case Some(sharedDbName) =>
        getDbHolderOrThrow(sharedDbName).profile
    }
  }
}

/**
 * A DbHolder lazily initializes a database
 * @param config The configuration used to create the database
 * @param addShutDownHook If true a `system.registerOnTermination` will be used to register a callback which
 *                        closes the database when the actor system terminates
 */
class DbHolder(config: Config, addShutDownHook: Boolean, system: ActorSystem) {
  val profile: JdbcProfile = SlickDriver.forDriverName(config, path = "")

  lazy val db: Database = {
    implicit val ec: ExecutionContext = system.dispatcher
    val db = SlickDatabase.forConfig(config, new SlickConfiguration(config), path = "db")
    system.registerOnTermination {
      db.close()

    }
    db
  }
}
