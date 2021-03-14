/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.migrator

import akka.Done
import akka.actor.ActorSystem
import akka.persistence.PersistentRepr
import akka.persistence.jdbc.config.{ JournalConfig, ReadJournalConfig }
import akka.persistence.jdbc.db.SlickExtension
import akka.persistence.jdbc.journal.dao.{ legacy, AkkaSerialization, JournalQueries }
import akka.persistence.jdbc.journal.dao.legacy.ByteArrayJournalSerializer
import akka.persistence.jdbc.journal.dao.JournalTables.{ JournalAkkaSerializationRow, TagRow }
import akka.persistence.jdbc.query.dao.legacy.ReadJournalQueries
import akka.persistence.jdbc.testkit.internal._
import akka.persistence.journal.Tagged
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.{ Sink, Source }
import slick.basic.DatabaseConfig
import slick.jdbc.{ JdbcProfile, ResultSetConcurrency, ResultSetType }
import slick.sql.FixedSqlAction

import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success }

/**
 * This will help migrate the legacy journal data onto the new journal schema with the
 * appropriate serialization
 *
 * @param system the actor system
 */
final case class JournalMigrator(schemaType: SchemaType)(implicit system: ActorSystem) {
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  // get the Jdbc Profile
  protected val profile: JdbcProfile = DatabaseConfig.forConfig[JdbcProfile]("slick").profile

  import profile.api._

  // get the various configurations
  private val journalConfig = new JournalConfig(system.settings.config.getConfig("jdbc-journal"))
  private val readJournalConfig = new ReadJournalConfig(system.settings.config.getConfig("jdbc-read-journal"))

  // the journal database
  private val journaldb =
    SlickExtension(system).database(system.settings.config.getConfig("jdbc-read-journal")).database

  // get an instance of the new journal queries
  private val newJournalQueries: JournalQueries =
    new JournalQueries(profile, journalConfig.eventJournalTableConfiguration, journalConfig.eventTagTableConfiguration)

  // let us get the journal reader
  private val serialization = SerializationExtension(system)
  private val legacyJournalQueries = new ReadJournalQueries(profile, readJournalConfig)
  private val serializer = new ByteArrayJournalSerializer(serialization, readJournalConfig.pluginConfig.tagSeparator)

  private val bufferSize: Int = journalConfig.daoConfig.bufferSize
  private val parallelism: Int = journalConfig.daoConfig.parallelism

  // get the journal ordering based upon the schema type used
  private val journalOrdering: JournalOrdering = schemaType match {
    case Postgres  => PostgresOrdering(journalConfig, newJournalQueries, journaldb)
    case MySQL     => MySQLOrdering(journalConfig, newJournalQueries, journaldb)
    case SqlServer => SqlServerOrdering(journalConfig, newJournalQueries, journaldb)
    case Oracle    => OracleOrdering(journalConfig, newJournalQueries, journaldb)
    case H2        => ???
  }

  /**
   * write all legacy events into the new journal tables applying the proper serialization
   */
  def migrate(): Future[Unit] = {
    val query: DBIOAction[Seq[legacy.JournalRow], Streaming[legacy.JournalRow], Effect.Read with Effect.Transactional] =
      legacyJournalQueries.JournalTable.result
        .withStatementParameters(
          rsType = ResultSetType.ForwardOnly,
          rsConcurrency = ResultSetConcurrency.ReadOnly,
          fetchSize = bufferSize)
        .transactionally

    val eventualDone: Future[Done] = Source
      .fromPublisher(journaldb.stream(query))
      .via(serializer.deserializeFlow)
      .mapAsync(parallelism)(reprAndOrdNr => Future.fromTry(reprAndOrdNr))
      .map { case (repr, tags, ordering) => repr.withPayload(Tagged(repr, tags)) -> ordering }
      // since the write is done one at a time we can at least enhance throughput by
      // spawning more actors to execute the db write
      .mapAsync(parallelism) { case (repr, ordering) =>
        // serializing the PersistentRepr using the same ordering received from the old journal
        val (row, tags): (JournalAkkaSerializationRow, Set[String]) = serialize(repr, ordering)
        // persist the data
        writeJournalRows(row, tags)
      }
      .runWith(Sink.ignore)

    // run the data migration and set the next ordering value
    for {
      _ <- eventualDone
      _ <- journalOrdering.setVal()
    } yield ()
  }

  /**
   * Unpack a PersistentRepr into a PersistentRepr and set of tags.
   * It returns a tuple containing the PersistentRepr and its set of tags
   *
   * @param pr the given PersistentRepr
   */
  private def unpackPersistentRepr(pr: PersistentRepr): (PersistentRepr, Set[String]) = {
    pr.payload match {
      case Tagged(payload, tags) => (pr.withPayload(payload), tags)
      case _                     => (pr, Set.empty[String])
    }
  }

  /**
   * serialize the PersistentRepr and construct a JournalAkkaSerializationRow and set of matching tags
   *
   * @param pr       the PersistentRepr
   * @param ordering the ordering of the PersistentRepr
   * @return the tuple of JournalAkkaSerializationRow and set of tags
   */
  private def serialize(pr: PersistentRepr, ordering: Long): (JournalAkkaSerializationRow, Set[String]) = {

    // let us unpack the PersistentRepr
    val (repr, tags) = unpackPersistentRepr(pr)

    val serializedPayload: AkkaSerialization.AkkaSerialized =
      AkkaSerialization.serialize(serialization, repr.payload) match {
        case Failure(exception) => throw exception
        case Success(value)     => value
      }

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

  /**
   * inserts a serialized journal row with the mapping tags
   *
   * @param journalSerializedRow the serialized journal row
   * @param tags                 the set of tags
   */
  private def writeJournalRows(journalSerializedRow: JournalAkkaSerializationRow, tags: Set[String]): Future[Unit] = {
    val journalInsert: DBIO[Long] = newJournalQueries.JournalTable
      .returning(newJournalQueries.JournalTable.map(_.ordering))
      .forceInsert(
        journalSerializedRow
      ) // here we force the insertion of the auto-inc value to maintain the old ordering

    val tagInserts: FixedSqlAction[Option[Int], NoStream, Effect.Write] =
      newJournalQueries.TagTable ++= tags.map(tag => TagRow(journalSerializedRow.ordering, tag)).toSeq

    journaldb.run(DBIO.seq(journalInsert, tagInserts).withPinnedSession.transactionally)
  }
}
