package akka.persistence.jdbc.snapshot.dao

import slick.jdbc.{ JdbcBackend, JdbcProfile }
import akka.persistence.SnapshotMetadata
import akka.persistence.jdbc.config.SnapshotConfig
import akka.serialization.Serialization
import akka.stream.Materializer
import SnapshotTables._
import akka.dispatch.ExecutionContexts
import akka.persistence.jdbc.journal.dao.AkkaSerialization

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class AkkaSerializerSnapshotDao(
    db: JdbcBackend#Database,
    profile: JdbcProfile,
    snapshotConfig: SnapshotConfig,
    serialization: Serialization)(implicit ec: ExecutionContext, val mat: Materializer)
    extends SnapshotDao {
  import profile.api._
  val queries = new SnapshotQueries(profile, snapshotConfig.snapshotTableConfiguration)

  private def toSnapshotData(row: SnapshotRow): Try[(SnapshotMetadata, Any)] = {
    // TODO RES metadata
    val snapshot = serialization.deserialize(row.snapshotPayload, row.snapshotSerId, row.snapshotSerManifest)
    snapshot.map(snapshot => (SnapshotMetadata(row.persistenceId, row.sequenceNumber, row.created), snapshot))
  }

  private def serializeSnapshot(meta: SnapshotMetadata, snapshot: Any): Try[SnapshotRow] = {
    // TODO RES metadata
    AkkaSerialization
      .serialize(serialization, payload = snapshot)
      .map(
        serializedSnapshot =>
          SnapshotRow(
            meta.persistenceId,
            meta.sequenceNr,
            meta.timestamp,
            serializedSnapshot.serId,
            serializedSnapshot.serManifest,
            serializedSnapshot.payload,
            None,
            None,
            None))
  }

  private def zeroOrOneSnapshot(rows: Seq[SnapshotRow]): Option[(SnapshotMetadata, Any)] =
    // TODO maybe throw instead of toOption otherwise serialization errors will not be logged
    rows.headOption.flatMap(row => toSnapshotData(row).toOption)

  override def latestSnapshot(persistenceId: String): Future[Option[(SnapshotMetadata, Any)]] =
    db.run(queries.selectLatestByPersistenceId(persistenceId).result).map { rows =>
      rows.headOption.flatMap(row => toSnapshotData(row).toOption)
    }

  override def snapshotForMaxTimestamp(
      persistenceId: String,
      maxTimestamp: Long): Future[Option[(SnapshotMetadata, Any)]] =
    db.run(queries.selectOneByPersistenceIdAndMaxTimestamp(persistenceId, maxTimestamp).result).map(zeroOrOneSnapshot)

  override def snapshotForMaxSequenceNr(
      persistenceId: String,
      maxSequenceNr: Long): Future[Option[(SnapshotMetadata, Any)]] =
    db.run(queries.selectOneByPersistenceIdAndMaxSequenceNr(persistenceId, maxSequenceNr).result).map(zeroOrOneSnapshot)

  override def snapshotForMaxSequenceNrAndMaxTimestamp(
      persistenceId: String,
      maxSequenceNr: Long,
      maxTimestamp: Long): Future[Option[(SnapshotMetadata, Any)]] =
    db.run(
      queries
        .selectOneByPersistenceIdAndMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp)
        .result)
      .map(zeroOrOneSnapshot)

  override def save(snapshotMetadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    val eventualSnapshotRow = Future.fromTry(serializeSnapshot(snapshotMetadata, snapshot))
    eventualSnapshotRow.map(queries.insertOrUpdate).flatMap(db.run).map(_ => ())(ExecutionContexts.parasitic)
  }

  override def delete(persistenceId: String, sequenceNr: Long): Future[Unit] =
    db.run(queries.selectByPersistenceIdAndSequenceNr(persistenceId, sequenceNr).delete)
      .map(_ => ())(ExecutionContexts.parasitic)

  override def deleteAllSnapshots(persistenceId: String): Future[Unit] =
    db.run(queries.selectAll(persistenceId).delete).map(_ => ())((ExecutionContexts.parasitic))

  override def deleteUpToMaxSequenceNr(persistenceId: String, maxSequenceNr: Long): Future[Unit] =
    db.run(queries.selectByPersistenceIdUpToMaxSequenceNr(persistenceId, maxSequenceNr).delete)
      .map(_ => ())((ExecutionContexts.parasitic))

  override def deleteUpToMaxTimestamp(persistenceId: String, maxTimestamp: Long): Future[Unit] =
    db.run(queries.selectByPersistenceIdUpToMaxTimestamp(persistenceId, maxTimestamp).delete)
      .map(_ => ())((ExecutionContexts.parasitic))

  override def deleteUpToMaxSequenceNrAndMaxTimestamp(
      persistenceId: String,
      maxSequenceNr: Long,
      maxTimestamp: Long): Future[Unit] =
    db.run(
      queries.selectByPersistenceIdUpToMaxSequenceNrAndMaxTimestamp(persistenceId, maxSequenceNr, maxTimestamp).delete)
      .map(_ => ())((ExecutionContexts.parasitic))
}
