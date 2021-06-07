/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.journal.dao

import akka.NotUsed
import akka.persistence.jdbc.AkkaSerialization
import akka.persistence.jdbc.config.{ BaseDaoConfig, JournalConfig }
import akka.persistence.jdbc.journal.dao.JournalTables.JournalAkkaSerializationRow
import akka.persistence.journal.Tagged
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.serialization.Serialization
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.JdbcProfile

import scala.collection.immutable
import scala.collection.immutable.{ Nil, Seq }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

/**
 * A [[JournalDao]] that uses Akka serialization to serialize the payload and store
 * the manifest and serializer id used.
 */
class DefaultJournalDao(
    val db: Database,
    val profile: JdbcProfile,
    val journalConfig: JournalConfig,
    serialization: Serialization)(implicit val ec: ExecutionContext, val mat: Materializer)
    extends BaseDao[(JournalAkkaSerializationRow, Set[String])]
    with BaseJournalDaoWithReadMessages
    with JournalDao
    with H2Compat {

  import profile.api._

  override def baseDaoConfig: BaseDaoConfig = journalConfig.daoConfig

  override def writeJournalRows(xs: immutable.Seq[(JournalAkkaSerializationRow, Set[String])]): Future[Unit] = {
    db.run(queries.writeJournalRows(xs).transactionally).map(_ => ())
  }

  val queries =
    new JournalQueries(profile, journalConfig.eventJournalTableConfiguration, journalConfig.eventTagTableConfiguration)

  override def delete(persistenceId: String, maxSequenceNr: Long): Future[Unit] = {
    val actions: DBIOAction[Unit, NoStream, Effect.Write with Effect.Read] = for {
      _ <- queries.markJournalMessagesAsDeleted(persistenceId, maxSequenceNr)
      highestMarkedSequenceNr <- highestMarkedSequenceNr(persistenceId)
      _ <- queries.delete(persistenceId, highestMarkedSequenceNr.getOrElse(0L) - 1)
    } yield ()

    db.run(actions.transactionally)
  }

  override def highestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    for {
      maybeHighestSeqNo <- db.run(queries.highestSequenceNrForPersistenceId(persistenceId).result)
    } yield maybeHighestSeqNo.getOrElse(0L)
  }

  private def highestMarkedSequenceNr(persistenceId: String) =
    queries.highestMarkedSequenceNrForPersistenceId(persistenceId).result

  override def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {

    def serializeAtomicWrite(aw: AtomicWrite): Try[Seq[(JournalAkkaSerializationRow, Set[String])]] = {
      Try(aw.payload.map(serialize))
    }

    def serialize(pr: PersistentRepr): (JournalAkkaSerializationRow, Set[String]) = {

      val (updatedPr, tags) = pr.payload match {
        case Tagged(payload, tags) => (pr.withPayload(payload), tags)
        case _                     => (pr, Set.empty[String])
      }

      val serializedPayload = AkkaSerialization.serialize(serialization, updatedPr.payload).get
      val serializedMetadata = updatedPr.metadata.flatMap(m => AkkaSerialization.serialize(serialization, m).toOption)
      val row = JournalAkkaSerializationRow(
        Long.MinValue,
        updatedPr.deleted,
        updatedPr.persistenceId,
        updatedPr.sequenceNr,
        updatedPr.writerUuid,
        updatedPr.timestamp,
        updatedPr.manifest,
        serializedPayload.payload,
        serializedPayload.serId,
        serializedPayload.serManifest,
        serializedMetadata.map(_.payload),
        serializedMetadata.map(_.serId),
        serializedMetadata.map(_.serManifest))

      (row, tags)
    }

    val serializedTries = messages.map(serializeAtomicWrite)

    val rowsToWrite: Seq[(JournalAkkaSerializationRow, Set[String])] = for {
      serializeTry <- serializedTries
      row <- serializeTry.getOrElse(Seq.empty)
    } yield row

    def resultWhenWriteComplete =
      if (serializedTries.forall(_.isSuccess)) Nil else serializedTries.map(_.map(_ => ()))

    queueWriteJournalRows(rowsToWrite).map(_ => resultWhenWriteComplete)
  }

  override def messages(
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long,
      max: Long): Source[Try[(PersistentRepr, Long)], NotUsed] = {
    Source
      .fromPublisher(
        db.stream(
          queries.messagesQuery((persistenceId, fromSequenceNr, toSequenceNr, correctMaxForH2Driver(max))).result))
      .map(AkkaSerialization.fromRow(serialization)(_))
  }
}
