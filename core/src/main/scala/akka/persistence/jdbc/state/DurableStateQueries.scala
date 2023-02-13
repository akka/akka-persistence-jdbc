/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2022 Lightbend Inc. <https://www.lightbend.com>
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

  implicit val uuidSetter = SetParameter[Array[Byte]] { case (bytes, params) =>
    params.setBytes(bytes)
  }

  private[jdbc] def selectFromDbByPersistenceId(persistenceId: Rep[String]) =
    durableStateTable.filter(_.persistenceId === persistenceId)

  private[jdbc] def insertDbWithDurableState(row: DurableStateTables.DurableStateRow, seqNextValue: String) = {
    durableStateTable += row.copy(globalOffset = seqNextValue, stateTimestamp = System.currentTimeMillis())
  }

  private[jdbc] def updateDbWithDurableState(row: DurableStateTables.DurableStateRow, seqNextValue: String) = {
    durableStateTable
      .filter(t =>
        t.persistenceId === row.persistenceId && t.revision === (row.revision - 1)
      )
      .map(t =>
        (
          t.globalOffset,
          t.revision,
          t.statePayload,
          t.stateSerId,
          t.stateSerManifest,
          t.tag,
          t.stateTimestamp
        )
      )
      .update(
        (
          seqNextValue,
          row.revision,
          row.statePayload,
          row.stateSerId,
          row.stateSerManifest,
          row.tag,
          System.currentTimeMillis()
        )
      )
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
