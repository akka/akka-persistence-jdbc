/*
 * Copyright 2015 Dennis Vriend
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

package akka.persistence.jdbc.extension

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.event.{Logging, LoggingAdapter}

object AkkaPersistenceConfig extends ExtensionId[AkkaPersistenceConfigImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): AkkaPersistenceConfigImpl = new AkkaPersistenceConfigImpl()(system)

  override def lookup(): ExtensionId[_ <: Extension] = AkkaPersistenceConfig
}

trait AkkaPersistenceConfig {
  def slickDriver: String
  def driverClass: String
  def url: String
  def user: String
  def password: String

  def executorName: String
  def numThreads: Int
  def queueSize: Int
}

class AkkaPersistenceConfigImpl()(implicit val system: ExtendedActorSystem) extends AkkaPersistenceConfig with Extension {
  val log: LoggingAdapter = Logging(system, this.getClass)

  val cfg = system.settings.config.getConfig("akka-persistence-jdbc")

  log.debug(
    s"""
      | ====================================
      | Akka Persistence JDBC Configuration:
      | ====================================
      | slickDriver: [$slickDriver]
      | driverClass: [$driverClass]
      | url: [$url]
      | user: [$user]
      | password: [$password]
      | executorName: [$executorName]
      | queueSize: [$queueSize]
      | numThreads: [$numThreads]
      | ====================================
    """.stripMargin)

  override def slickDriver: String = cfg.getString("slick.driver")

  override def driverClass: String = cfg.getString("slick.jdbcDriverClass")

  override def url: String = cfg.getString("slick.url")

  override def user: String = cfg.getString("slick.user")

  override def password: String = cfg.getString("slick.password")

  override def executorName: String = cfg.getString("slick.executor.name")

  override def queueSize: Int = cfg.getInt("slick.executor.queueSize")

  override def numThreads: Int = cfg.getInt("slick.executor.numThreads")
}
