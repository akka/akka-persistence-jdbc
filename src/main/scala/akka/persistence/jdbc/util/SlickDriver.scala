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
import javax.sql.DataSource

import akka.persistence.jdbc.config.{AsyncExecutorConfig, JournalConfig, SlickConfiguration}
import com.typesafe.config.Config
import slick.SlickException
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcBackend._
import slick.util.AsyncExecutor

object SlickDriver {
  def forDriverName(config: Config): JdbcProfile =
    DatabaseConfig.forConfig[JdbcProfile]("slick", config).profile
}

object SlickDatabase {

  def forConfig(config: Config, slickConfiguration: SlickConfiguration): Database = {

    val asyncExecConfig = slickConfiguration.asyncExecutorConfig

    val namingContext = new InitialContext()

    // builds a Slick Database from using a JNDI DataSource
    // together with a properly configured AsyncExecutor
    def buildDbFromJndiDatasource(name: String) = {
      namingContext.lookup(name) match {
        case dataSource: DataSource => Database.forDataSource(
          ds = dataSource,
          maxConnections = Option(asyncExecConfig.maxConnections),
          executor = AsyncExecutor(
            name = "AsyncExecutor.default",
            minThreads = asyncExecConfig.minConnections,
            maxThreads = asyncExecConfig.numThreads,
            queueSize = asyncExecConfig.queueSize,
            maxConnections = asyncExecConfig.maxConnections
          )
        )
        case x => throw new SlickException("Expected a DataSource for JNDI name " + name + ", but got " + x)
      }
    }

    slickConfiguration.jndiName
      .map(buildDbFromJndiDatasource)
      .orElse {
        slickConfiguration.jndiDbName.map(namingContext.lookup(_).asInstanceOf[Database])
      }
      .getOrElse(Database.forConfig("slick.db", config))
  }
}