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

import akka.annotation.InternalApi
import javax.naming.InitialContext
import akka.persistence.jdbc.config.SlickConfiguration
import com.typesafe.config.Config
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcBackend._

/**
 * INTERNAL API
 */
object SlickDriver {

  /**
   * INTERNAL API
   */
  @deprecated(message = "Internal API, will be removed in 4.0.0", since = "3.4.0")
  def forDriverName(config: Config): JdbcProfile =
    forDriverName(config, "slick")

  /**
   * INTERNAL API
   */
  private[jdbc] def forDriverName(config: Config, path: String): JdbcProfile =
    DatabaseConfig.forConfig[JdbcProfile](path, config).profile
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
    forConfig(config, slickConfiguration, "slick.db")
  }

  /**
   * INTERNAL API
   */
  private[jdbc] def forConfig(config: Config, slickConfiguration: SlickConfiguration, path: String): Database = {
    slickConfiguration.jndiName
      .map(Database.forName(_, None))
      .orElse {
        slickConfiguration.jndiDbName.map(
          new InitialContext().lookup(_).asInstanceOf[Database])
      }
      .getOrElse(Database.forConfig(path, config))
  }
}
