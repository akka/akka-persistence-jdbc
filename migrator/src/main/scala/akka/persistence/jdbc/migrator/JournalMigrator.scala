/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.migrator

import akka.Done
import akka.actor.ActorSystem
import akka.persistence.PersistentRepr
import akka.persistence.jdbc.AkkaSerialization
import akka.persistence.jdbc.config.{ JournalConfig, ReadJournalConfig }
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.journal.dao.JournalQueries
import akka.persistence.jdbc.journal.dao.legacy.ByteArrayJournalSerializer
import akka.persistence.jdbc.journal.dao.JournalTables.{ JournalAkkaSerializationRow, TagRow }
import akka.persistence.jdbc.migrator.JournalMigrator.{ JournalConfig, ReadJournalConfig }
import akka.persistence.jdbc.query.dao.legacy.ReadJournalQueries
import akka.serialization.{ Serialization, SerializationExtension }
import akka.stream.scaladsl.Source
import org.slf4j.{ Logger, LoggerFactory }
import slick.jdbc._

import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success }

/**
 * This will help migrate the legacy journal data onto the new journal schema with the
 * appropriate serialization
 *
 * @param system the actor system
 */
final case class JournalMigrator(profile: JdbcProfile)(implicit system: ActorSystem) {
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  import profile.api._

  val log: Logger = LoggerFactory.getLogger(getClass)

  // get the various configurations
  private val journalConfig: JournalConfig = new JournalConfig(system.settings.config.getConfig(JournalConfig))
  private val readJournalConfig: ReadJournalConfig = new ReadJournalConfig(
    system.settings.config.getConfig(ReadJournalConfig))

  // the journal database
  private val journalDB: JdbcBackend.Database =
    SlickExtension(system).database(system.settings.config.getConfig(ReadJournalConfig)).database

  // get an instance of the new journal queries
  private val newJournalQueries: JournalQueries =
    new JournalQueries(profile, journalConfig.eventJournalTableConfiguration, journalConfig.eventTagTableConfiguration)

  // let us get the journal reader
  private val serialization: Serialization = SerializationExtension(system)
  private val legacyJournalQueries: ReadJournalQueries = new ReadJournalQueries(profile, readJournalConfig)
  private val serializer: ByteArrayJournalSerializer =
    new ByteArrayJournalSerializer(serialization, readJournalConfig.pluginConfig.tagSeparator)

  private val bufferSize: Int = journalConfig.daoConfig.bufferSize

  private val query =
    legacyJournalQueries.JournalTable.result
      .withStatementParameters(
        rsType = ResultSetType.ForwardOnly,
        rsConcurrency = ResultSetConcurrency.ReadOnly,
        fetchSize = bufferSize)
      .transactionally

  /**
   * write all legacy events into the new journal tables applying the proper serialization
   */
  def migrate(): Future[Done] = Source
    .fromPublisher(journalDB.stream(query))
    .via(serializer.deserializeFlow)
    .map {
      case Success((repr, tags, ordering)) => (repr, tags, ordering)
      case Failure(exception)              => throw exception // blow-up on failure
    }
    .map { case (repr, tags, ordering) => serialize(repr, tags, ordering) }
    // get pages of many records at once
    .grouped(bufferSize)
    .mapAsync(1)(records => {
      val stmt: DBIO[Unit] = records
        // get all the sql statements for this record as an option
        .map { case (newRepr, newTags) =>
          log.debug(s"migrating event for PersistenceID: ${newRepr.persistenceId} with tags ${newTags.mkString(",")}")
          writeJournalRowsStatements(newRepr, newTags)
        }
        // reduce to 1 statement
        .foldLeft[DBIO[Unit]](DBIO.successful[Unit] {})((priorStmt, nextStmt) => {
          priorStmt.andThen(nextStmt)
        })

      journalDB.run(stmt)
    })
    .run()

  /**
   * serialize the PersistentRepr and construct a JournalAkkaSerializationRow and set of matching tags
   *
   * @param repr the PersistentRepr
   * @param tags the tags
   * @param ordering the ordering of the PersistentRepr
   * @return the tuple of JournalAkkaSerializationRow and set of tags
   */
  private def serialize(
      repr: PersistentRepr,
      tags: Set[String],
      ordering: Long): (JournalAkkaSerializationRow, Set[String]) = {

    val serializedPayload: AkkaSerialization.AkkaSerialized =
      AkkaSerialization.serialize(serialization, repr.payload).get

    val serializedMetadata: Option[AkkaSerialization.AkkaSerialized] =
      repr.metadata.flatMap(m => AkkaSerialization.serialize(serialization, m).toOption)
    val row: JournalAkkaSerializationRow = JournalAkkaSerializationRow(
      ordering,
      repr.deleted,
      repr.persistenceId,
      repr.sequenceNr,
      repr.writerUuid,
      repr.timestamp,
      repr.manifest,
      serializedPayload.payload,
      serializedPayload.serId,
      serializedPayload.serManifest,
      serializedMetadata.map(_.payload),
      serializedMetadata.map(_.serId),
      serializedMetadata.map(_.serManifest))

    (row, tags)
  }

  private def writeJournalRowsStatements(
      journalSerializedRow: JournalAkkaSerializationRow,
      tags: Set[String]): DBIO[Unit] = {
    val journalInsert: DBIO[Long] = newJournalQueries.JournalTable
      .returning(newJournalQueries.JournalTable.map(_.ordering))
      .forceInsert(journalSerializedRow)

    val tagInserts =
      newJournalQueries.TagTable ++= tags
        .map(tag =>
          TagRow(
            Some(journalSerializedRow.ordering),
            Some(journalSerializedRow.persistenceId),
            Some(journalSerializedRow.sequenceNumber),
            tag))
        .toSeq

    journalInsert.flatMap(_ => tagInserts.asInstanceOf[DBIO[Unit]])
  }
}

case object JournalMigrator {
  final val JournalConfig: String = "jdbc-journal"
  final val ReadJournalConfig: String = "jdbc-read-journal"
}
