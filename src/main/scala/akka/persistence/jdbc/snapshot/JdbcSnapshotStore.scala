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

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.persistence.jdbc.config.SnapshotConfig
import akka.persistence.jdbc.dao.SnapshotDao
import akka.persistence.jdbc.serialization.{ AkkaSerializationProxy, SerializationProxy }
import akka.persistence.jdbc.util.{ SlickDatabase, SlickDriver }
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{ SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria }
import akka.serialization.SerializationExtension
import akka.stream.{ ActorMaterializer, Materializer }
import com.typesafe.config.Config
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend._

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Success, Try }

object JdbcSnapshotStore {
  sealed trait SerializationResult {
    def metadata: SnapshotMetadata
  }

  final case class Serialized(metadata: SnapshotMetadata, bytes: Array[Byte]) extends SerializationResult

  final case class NotSerialized(metadata: SnapshotMetadata, snapshot: Any) extends SerializationResult

  def mapToSelectedSnapshot(serializationResult: SerializationResult, serializationProxy: SerializationProxy): Try[SelectedSnapshot] =
    serializationResult match {
      case Serialized(meta, bytes)       ⇒ serializationProxy.deserialize(bytes, classOf[Snapshot]).map(snapshot ⇒ SelectedSnapshot(meta, snapshot.data))
      case NotSerialized(meta, snapshot) ⇒ Success(SelectedSnapshot(meta, snapshot))
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
    val driver = snapshotConfig.slickConfiguration.slickDriver
    val fqcn = snapshotConfig.pluginConfig.dao
    val profile: JdbcProfile = SlickDriver.forDriverName(driver)
    val args = immutable.Seq(
      (classOf[Database], db),
      (classOf[JdbcProfile], profile),
      (classOf[SnapshotConfig], snapshotConfig),
      (classOf[ExecutionContext], ec),
      (classOf[Materializer], mat)
    )
    system.asInstanceOf[ExtendedActorSystem].dynamicAccess.createInstanceFor[SnapshotDao](fqcn, args).get
  }

  val serializationProxy = new AkkaSerializationProxy(SerializationExtension(system))

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
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

    for {
      snapshotDataOption ← result
      selectedSnapshot = for {
        snapshotData: SerializationResult ← snapshotDataOption
        selectedSnapshot: SelectedSnapshot ← mapToSelectedSnapshot(snapshotData, serializationProxy).toOption
      } yield selectedSnapshot
    } yield selectedSnapshot
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = for {
    serializationResult ← if (snapshotConfig.pluginConfig.serialization) Future.fromTry(serializationProxy.serialize(Snapshot(snapshot))).map(arr ⇒ Serialized(metadata, arr))
    else Future.successful(NotSerialized(metadata, snapshot))
    _ ← snapshotDao.save(metadata.persistenceId, metadata.sequenceNr, metadata.timestamp, serializationResult)
  } yield ()

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
