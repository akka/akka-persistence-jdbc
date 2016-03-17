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

import akka.actor._
import akka.event.{ Logging, LoggingAdapter }
import akka.persistence.jdbc.dao.inmemory.{ InMemoryJournalDao, InMemoryJournalStorage, InMemorySnapshotDao, InMemorySnapshotStorage }
import akka.persistence.jdbc.dao.{ JournalDao, SnapshotDao }
import akka.persistence.jdbc.util.SlickDriver
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.Timeout
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend

import scala.collection.immutable
import scala.concurrent.ExecutionContext

object DaoRepository extends ExtensionId[DaoRepositoryImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): DaoRepositoryImpl = new DaoRepositoryImpl()(system)

  override def lookup(): ExtensionId[_ <: Extension] = DaoRepository
}

trait DaoRepository {
  def journalDao: JournalDao

  def snapshotDao: SnapshotDao
}

class DaoRepositoryImpl()(implicit val system: ExtendedActorSystem) extends DaoRepository with Extension {
  implicit val ec: ExecutionContext = system.dispatcher

  implicit val mat: Materializer = ActorMaterializer()

  val log: LoggingAdapter = Logging(system, this.getClass)

  lazy val journalStorage = system.actorOf(Props(new InMemoryJournalStorage), "InMemoryJournalStorage")

  lazy val snapshotStorage = system.actorOf(Props(new InMemorySnapshotStorage), "InMemorySnapshotStorage")

  override def journalDao: JournalDao =
    if (AkkaPersistenceConfig(system).inMemory) {
      import scala.concurrent.duration._
      implicit val timeout = Timeout(AkkaPersistenceConfig(system).inMemoryTimeout)
      InMemoryJournalDao(journalStorage)
    } else {
      val driver = AkkaPersistenceConfig(system).slickConfiguration.slickDriver
      val fqcn = system.settings.config.getString("akka-persistence-jdbc.dao.journal")
      system.dynamicAccess.createInstanceFor[JournalDao](fqcn, immutable.Seq(
        (classOf[JdbcBackend#DatabaseDef], SlickDatabase(system).db),
        (classOf[JdbcProfile], SlickDriver.forDriverName(driver)),
        (classOf[ActorSystem], system)
      )).get
    }

  override def snapshotDao: SnapshotDao =
    if (AkkaPersistenceConfig(system).inMemory) {
      import scala.concurrent.duration._
      implicit val timeout = Timeout(5.seconds)
      InMemorySnapshotDao(snapshotStorage)
    } else {
      val driver = AkkaPersistenceConfig(system).slickConfiguration.slickDriver
      val fqcn = system.settings.config.getString("akka-persistence-jdbc.dao.snapshot")
      system.dynamicAccess.createInstanceFor[SnapshotDao](fqcn, immutable.Seq(
        (classOf[JdbcBackend#DatabaseDef], SlickDatabase(system).db),
        (classOf[JdbcProfile], SlickDriver.forDriverName(driver)),
        (classOf[ActorSystem], system)
      )).get
    }
}
