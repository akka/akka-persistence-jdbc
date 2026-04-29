/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2025 Lightbend Inc. <https://akka.io>
 */

package akka.persistence.jdbc.state

import akka.annotation.InternalApi
import slick.jdbc.{ JdbcProfile, SetParameter }
import slick.jdbc.H2Profile
import slick.jdbc.MySQLProfile
import slick.jdbc.OracleProfile
import slick.jdbc.PostgresProfile
import slick.jdbc.SQLServerProfile
import akka.persistence.jdbc.config.DurableStateTableConfiguration

/**
 * INTERNAL API
 */
@InternalApi private[akka] class DurableStateQueries(
    val profile: JdbcProfile,
    override val durableStateTableCfg: DurableStateTableConfiguration)
    extends DurableStateTables {
  import profile.api._

  // Identifiers must be quoted via the profile so the raw-SQL INSERT/UPDATE paths use the
  // same quoting as Slick's typed-query SELECT path. Without this, e.g. H2 in default mode
  // uppercases unquoted identifiers, which doesn't match the lowercase quoted identifiers
  // used by the schema and Slick's typed queries.
  val tableAndSchema =
    durableStateTableCfg.schemaName.fold(profile.quoteIdentifier(durableStateTableCfg.tableName))(schema =>
      s"${profile.quoteIdentifier(schema)}.${profile.quoteIdentifier(durableStateTableCfg.tableName)}")

  private val persistenceIdColumn = profile.quoteIdentifier(durableStateTableCfg.columnNames.persistenceId)
  private val globalOffsetColumn = profile.quoteIdentifier(durableStateTableCfg.columnNames.globalOffset)
  private val revisionColumn = profile.quoteIdentifier(durableStateTableCfg.columnNames.revision)
  private val statePayloadColumn = profile.quoteIdentifier(durableStateTableCfg.columnNames.statePayload)
  private val stateSerIdColumn = profile.quoteIdentifier(durableStateTableCfg.columnNames.stateSerId)
  private val stateSerManifestColumn = profile.quoteIdentifier(durableStateTableCfg.columnNames.stateSerManifest)
  private val tagColumn = profile.quoteIdentifier(durableStateTableCfg.columnNames.tag)
  private val stateTimestampColumn = profile.quoteIdentifier(durableStateTableCfg.columnNames.stateTimestamp)

  private def slickProfileToSchemaType(profile: JdbcProfile): String =
    profile match {
      case PostgresProfile  => "Postgres"
      case MySQLProfile     => "MySQL"
      case OracleProfile    => "Oracle"
      case SQLServerProfile => "SqlServer"
      case H2Profile        => "H2"
      case _                => throw new IllegalArgumentException(s"Unknown JdbcProfile $profile encountered")
    }

  lazy val sequenceNextValUpdater = slickProfileToSchemaType(profile) match {
    case "H2"       => new H2SequenceNextValUpdater(profile, durableStateTableCfg)
    case "Postgres" => new PostgresSequenceNextValUpdater(profile, durableStateTableCfg)
    case _          => ???
  }

  implicit val uuidSetter: SetParameter[Array[Byte]] = SetParameter[Array[Byte]] { case (bytes, params) =>
    params.setBytes(bytes)
  }

  private[jdbc] def selectFromDbByPersistenceId(persistenceId: Rep[String]) =
    durableStateTable.filter(_.persistenceId === persistenceId)

  private[jdbc] def insertDbWithDurableState(row: DurableStateTables.DurableStateRow, seqNextValue: String) = {
    sqlu"""INSERT INTO #$tableAndSchema
            (
             #$persistenceIdColumn,
             #$globalOffsetColumn,
             #$revisionColumn,
             #$statePayloadColumn,
             #$stateSerIdColumn,
             #$stateSerManifestColumn,
             #$tagColumn,
             #$stateTimestampColumn
            )
            VALUES
            (
              ${row.persistenceId},
              #${seqNextValue},
              ${row.revision},
              ${row.statePayload},
              ${row.stateSerId},
              ${row.stateSerManifest},
              ${row.tag},
              #${System.currentTimeMillis()}
            )
      """
  }

  private[jdbc] def updateDbWithDurableState(row: DurableStateTables.DurableStateRow, seqNextValue: String) = {
    sqlu"""UPDATE #$tableAndSchema
           SET #$globalOffsetColumn = #${seqNextValue},
               #$revisionColumn = ${row.revision},
               #$statePayloadColumn = ${row.statePayload},
               #$stateSerIdColumn = ${row.stateSerId},
               #$stateSerManifestColumn = ${row.stateSerManifest},
               #$tagColumn = ${row.tag},
               #$stateTimestampColumn = ${System.currentTimeMillis}
           WHERE #$persistenceIdColumn = ${row.persistenceId}
             AND #$revisionColumn = ${row.revision} - 1
        """
  }

  private[jdbc] def getSequenceNextValueExpr() = sequenceNextValUpdater.getSequenceNextValueExpr()

  def deleteFromDb(persistenceId: String) = {
    durableStateTable.filter(_.persistenceId === persistenceId).delete
  }

  def deleteAllFromDb() = {
    durableStateTable.delete
  }

  private[jdbc] val maxOffsetQuery = Compiled {
    durableStateTable.map(_.globalOffset).max.getOrElse(0L)
  }

  private def _changesByTag(
      tag: Rep[String],
      offset: ConstColumn[Long],
      maxOffset: ConstColumn[Long],
      max: ConstColumn[Long]) = {
    durableStateTable
      .filter(_.tag === tag)
      .sortBy(_.globalOffset.asc)
      .filter(row => row.globalOffset > offset && row.globalOffset <= maxOffset)
      .take(max)
  }

  private[jdbc] val changesByTag = Compiled(_changesByTag _)

  private def _stateStoreStateQuery(from: ConstColumn[Long], limit: ConstColumn[Long]) =
    durableStateTable // FIXME change this to a specialized query to only retrieve the 3 columns of interest
      .filter(_.globalOffset > from)
      .sortBy(_.globalOffset.asc)
      .take(limit)
      .map(s => (s.persistenceId, s.globalOffset, s.revision))

  val stateStoreStateQuery = Compiled(_stateStoreStateQuery _)
}
