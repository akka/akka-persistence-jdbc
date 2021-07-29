/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.state

import slick.jdbc.{ JdbcProfile, SetParameter }
import slick.jdbc.H2Profile
import slick.jdbc.MySQLProfile
import slick.jdbc.OracleProfile
import slick.jdbc.PostgresProfile
import slick.jdbc.SQLServerProfile
import akka.persistence.jdbc.config.DurableStateTableConfiguration

class DurableStateQueries(val profile: JdbcProfile, override val durableStateTableCfg: DurableStateTableConfiguration)
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
    case "H2"       => new H2SequenceNextValUpdater(profile)
    case "Postgres" => new PostgresSequenceNextValUpdater(profile)
    case _          => ???
  }

  implicit val uuidSetter = SetParameter[Array[Byte]] { case (bytes, params) =>
    params.setBytes(bytes)
  }

  private[jdbc] def selectFromDbByPersistenceId(persistenceId: Rep[String]) =
    durableStateTable.filter(_.persistenceId === persistenceId)

  private[jdbc] def insertDbWithDurableState(row: DurableStateTables.DurableStateRow, seqNextValue: String) = {
    // TODO: read the table name and column names from durableStateTableCfg
    sqlu"""INSERT INTO durable_state
            (
              persistence_id,
              global_offset,
              revision,
              state_payload,
              state_serial_id,
              state_serial_manifest,
              tag,
              state_timestamp
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
    // TODO: read the table name and column names from durableStateTableCfg
    sqlu"""UPDATE durable_state
           SET global_offset = #${seqNextValue},
               revision = ${row.revision},
               state_payload = ${row.statePayload},
               state_serial_id = ${row.stateSerId}, 
               state_serial_manifest = ${row.stateSerManifest}, 
               tag = ${row.tag}, 
               state_timestamp = ${System.currentTimeMillis}
           WHERE persistence_id = ${row.persistenceId} 
             AND revision = ${row.revision} - 1
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
