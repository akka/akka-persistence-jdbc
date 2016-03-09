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

package akka.persistence.jdbc.snapshot

import akka.actor.ActorSystem
import akka.persistence.jdbc.dao.SnapshotDao
import akka.persistence.jdbc.extension.{ AkkaPersistenceConfig, DaoRepository }
import akka.persistence.jdbc.serialization.AkkaSerializationProxy
import akka.serialization.SerializationExtension
import akka.stream.{ ActorMaterializer, Materializer }

import scala.concurrent.ExecutionContext

class JdbcSnapshotStore extends SlickSnapshotStore {
  implicit val ec: ExecutionContext = context.dispatcher

  implicit val system: ActorSystem = context.system

  override implicit val mat: Materializer = ActorMaterializer()

  override val snapshotDao: SnapshotDao = DaoRepository(system).snapshotDao

  override val serializationProxy = new AkkaSerializationProxy(SerializationExtension(system))

  override val serialize: Boolean = AkkaPersistenceConfig(system).serializationConfiguration.snapshot
}
