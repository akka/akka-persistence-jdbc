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

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import slick.jdbc.JdbcBackend

object SlickDatabase extends ExtensionId[SlickDatabaseImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): SlickDatabaseImpl = new SlickDatabaseImpl()(system)

  override def lookup(): ExtensionId[_ <: Extension] = SlickDatabase
}

class SlickDatabaseImpl()(implicit val system: ExtendedActorSystem) extends JdbcBackend with Extension {
  val db: Database =
    if(AkkaPersistenceConfig(system).inMemory) null
    else if(AkkaPersistenceConfig(system).slickConfiguration.jndiName.isDefined)
      Database.forName(AkkaPersistenceConfig(system).slickConfiguration.jndiName.get)
    else Database.forConfig("akka-persistence-jdbc.slick.db", system.settings.config)
}
