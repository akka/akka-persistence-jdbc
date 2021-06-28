package akka.persistence.jdbc.state

import slick.jdbc.{ JdbcProfile, SetParameter }
import akka.persistence.jdbc.config.DurableStateTableConfiguration

class DurableStateQueries(val profile: JdbcProfile, override val durableStateTableCfg: DurableStateTableConfiguration)
    extends DurableStateTables {
  import profile.api._

  val sequenceNextValUpdater = durableStateTableCfg.schemaType match {
    case "H2"       => new H2SequenceNextValUpdater(profile)
    case "Postgres" => new PostgresSequenceNextValUpdater(profile)
  }

  implicit val uuidSetter = SetParameter[Array[Byte]] { case (bytes, params) =>
    params.setBytes(bytes)
  }

  private[jdbc] def selectFromDbByPersistenceId(persistenceId: Rep[String]) =
    durableStateTable.filter(_.persistenceId === persistenceId)

  private[jdbc] def insertDbWithDurableState(row: DurableStateTables.DurableStateRow, seqName: String) = {

    sqlu"""INSERT INTO state 
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
              #${seqName},
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
    sqlu"""UPDATE state 
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

  private def _stateStoreSequenceQuery(from: ConstColumn[Long], limit: ConstColumn[Long]) =
    durableStateTable.filter(_.globalOffset > from).map(_.globalOffset).sorted.take(limit)

  val stateStoreSequenceQuery = Compiled(_stateStoreSequenceQuery _)
}
