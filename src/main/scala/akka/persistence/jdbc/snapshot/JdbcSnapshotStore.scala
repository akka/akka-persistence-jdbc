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

import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.persistence.jdbc.config.SnapshotConfig
import akka.persistence.jdbc.dao.SnapshotDao
import akka.persistence.jdbc.util.{SlickDatabase, SlickDriver}
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.serialization.{Serialization, SerializationExtension}
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.Config
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend._

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

object JdbcSnapshotStore {

  def toSelectedSnapshot(tupled: (SnapshotMetadata, Any)): SelectedSnapshot = tupled match {
    case (meta: SnapshotMetadata, snapshot: Any) => SelectedSnapshot(meta, snapshot)
  }
}

class JdbcSnapshotStore(config: Config) extends SnapshotStore {
  import JdbcSnapshotStore._

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val system: ActorSystem = context.system
  implicit val mat: Materializer = ActorMaterializer()
  val snapshotConfig = new SnapshotConfig(config)

  val db: Database = SlickDatabase.forConfig(config, snapshotConfig.slickConfiguration)

  val snapshotDao: SnapshotDao = {
    val fqcn = snapshotConfig.pluginConfig.dao
    val profile: JdbcProfile = SlickDriver.forDriverName(config)
    val args = immutable.Seq(
      (classOf[Database], db),
      (classOf[JdbcProfile], profile),
      (classOf[SnapshotConfig], snapshotConfig),
      (classOf[Serialization], SerializationExtension(system)),
      (classOf[ExecutionContext], ec),
      (classOf[Materializer], mat)
    )
    system.asInstanceOf[ExtendedActorSystem].dynamicAccess.createInstanceFor[SnapshotDao](fqcn, args).get
  }

  override def loadAsync(persistenceId: String,
                         criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    val result = criteria match {
      case SnapshotSelectionCriteria(Long.MaxValue, Long.MaxValue, _, _) ⇒
        snapshotDao.snapshotForMaxSequenceNr(persistenceId)
      case SnapshotSelectionCriteria(Long.MaxValue, maxTimestamp, _, _) ⇒
        snapshotDao.snapshotForMaxTimestamp(persistenceId, maxTimestamp)
      case SnapshotSelectionCriteria(maxSequenceNr, Long.MaxValue, _, _) ⇒
        snapshotDao.snapshotForMaxSequenceNr(persistenceId, maxSequenceNr)
      case SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp, _, _) ⇒
        snapshotDao.snapshotForMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp)
      case _ ⇒ Future.successful(None)
    }

    result.map(_.map(toSelectedSnapshot))
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] =
    snapshotDao.save(metadata, snapshot)

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = for {
    _ ← snapshotDao.delete(metadata.persistenceId, metadata.sequenceNr)
  } yield ()

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = criteria match {
    case SnapshotSelectionCriteria(Long.MaxValue, Long.MaxValue, _, _) ⇒
      snapshotDao.deleteAllSnapshots(persistenceId)
    case SnapshotSelectionCriteria(Long.MaxValue, maxTimestamp, _, _) ⇒
      snapshotDao.deleteUpToMaxTimestamp(persistenceId, maxTimestamp)
    case SnapshotSelectionCriteria(maxSequenceNr, Long.MaxValue, _, _) ⇒
      snapshotDao.deleteUpToMaxSequenceNr(persistenceId, maxSequenceNr)
    case SnapshotSelectionCriteria(maxSequenceNr, maxTimestamp, _, _) ⇒
      snapshotDao.deleteUpToMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp)
    case _ ⇒ Future.successful(())
  }

  override def postStop(): Unit = {
    db.close()
    super.postStop()
  }
}
