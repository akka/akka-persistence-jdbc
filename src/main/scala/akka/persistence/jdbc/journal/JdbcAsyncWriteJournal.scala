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

package akka.persistence.jdbc.journal

import akka.actor.ActorSystem
import akka.persistence.jdbc.dao.JournalDao
import akka.persistence.jdbc.extension.{ AkkaPersistenceConfig, DaoRepository }
import akka.persistence.jdbc.serialization.SerializationFacade
import akka.stream.{ ActorMaterializer, Materializer }

import scala.concurrent.ExecutionContext

class JdbcAsyncWriteJournal extends SlickAsyncWriteJournal {
  implicit val ec: ExecutionContext = context.dispatcher

  implicit val system: ActorSystem = context.system

  override implicit val mat: Materializer = ActorMaterializer()

  override val journalDao: JournalDao = DaoRepository(system).journalDao

  override val serializationFacade: SerializationFacade =
    SerializationFacade(system, AkkaPersistenceConfig(context.system).persistenceQueryConfiguration.separator)
}
