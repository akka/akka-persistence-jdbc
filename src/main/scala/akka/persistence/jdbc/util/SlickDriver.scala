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

import javax.naming.InitialContext

import akka.persistence.jdbc.config.SlickConfiguration
import com.typesafe.config.Config
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcBackend._
import slick.jdbc.JdbcProfile

object SlickDriver {
  def forDriverName(config: Config): JdbcProfile =
    DatabaseConfig.forConfig[JdbcProfile]("slick", config).profile
}

object SlickDatabase {
  def forConfig(config: Config, slickConfiguration: SlickConfiguration): Database = {
    if (slickConfiguration.jndiName.isDefined)
      Database.forName(slickConfiguration.jndiName.get)
    else if (slickConfiguration.jndiDbName.isDefined)
      new InitialContext().lookup(slickConfiguration.jndiDbName.get).asInstanceOf[Database]
    else Database.forConfig("slick.db", config)
  }
}
