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

package akka.persistence.jdbc.extension

import javax.naming.InitialContext

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import slick.jdbc.JdbcBackend

import scala.concurrent.Future

object SlickDatabase extends ExtensionId[SlickDatabaseImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): SlickDatabaseImpl = new SlickDatabaseImpl()(system)

  override def lookup(): ExtensionId[_ <: Extension] = SlickDatabase
}

trait SlickDatabase {
  /**
   * Free all resources allocated by Slick for this Database. This is done asynchronously, so
   * you need to wait for the returned `Future` to complete in order to ensure that everything
   * has been shut down.
   */
  def shutdown: Future[Unit]

  /**
   * Free all resources allocated by Slick for this Database, blocking the current thread until
   * everything has been shut down.
   */
  def close(): Unit
}

class SlickDatabaseImpl()(implicit val system: ExtendedActorSystem) extends JdbcBackend with Extension with SlickDatabase {
  val db: Database =
    if (AkkaPersistenceConfig(system).inMemory) null
    else if (AkkaPersistenceConfig(system).slickConfiguration.jndiName.isDefined)
      Database.forName(AkkaPersistenceConfig(system).slickConfiguration.jndiName.get)
    else if (AkkaPersistenceConfig(system).slickConfiguration.jndiDbName.isDefined)
      new InitialContext().lookup(AkkaPersistenceConfig(system).slickConfiguration.jndiDbName.get).asInstanceOf[Database]
    else Database.forConfig("akka-persistence-jdbc.slick.db", system.settings.config)

  override def shutdown: Future[Unit] = db.shutdown

  override def close(): Unit = db.close()

  /**
   * Register closing the database connections after ActorSystem.shutdown has been issued.
   */
  system.registerOnTermination(close())
}
