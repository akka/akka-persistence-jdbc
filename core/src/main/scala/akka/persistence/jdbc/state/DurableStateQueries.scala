package akka.persistence.jdbc.state

import slick.jdbc.{ JdbcProfile, SetParameter }
import akka.persistence.jdbc.config.DurableStateTableConfiguration

class DurableStateQueries(val profile: JdbcProfile, override val durableStateTableCfg: DurableStateTableConfiguration)
    extends DurableStateTables {
  import profile.api._

  val sequenceNextValUpdater = durableStateTableCfg.schemaType match {
    case "H2"       => new H2SequenceNextValUpdater(profile)
    case "POSTGRES" => new PostgresSequenceNextValUpdater(profile)
  }

  implicit val uuidSetter = SetParameter[Array[Byte]] { case (bytes, params) =>
    params.setBytes(bytes)
  }

  private[jdbc] def _selectByPersistenceId(persistenceId: Rep[String]) =
    durableStateTable.filter(_.persistenceId === persistenceId)

  private[jdbc] def _selectByTag(tag: Rep[Option[String]], offset: Option[Long]) = {
    offset
      .map { o =>
        durableStateTable.filter(r => r.tag === tag && r.globalOffset > o)
      }
      .getOrElse {
        durableStateTable.filter(r => r.tag === tag)
      }
  }.sortBy(_.globalOffset.asc)

  private[jdbc] def _insertDurableState(row: DurableStateTables.DurableStateRow) =
    durableStateTable += row

  private[jdbc] def _updateDurableState(row: DurableStateTables.DurableStateRow, seqName: String) = {
    val str = sequenceNextValUpdater.nextValUpdater(seqName)
    sqlu"""UPDATE state 
           SET global_offset = #${str},
               sequence_number = ${row.seqNumber}, 
               state_payload = ${row.statePayload},
               state_serial_id = ${row.stateSerId}, 
               state_serial_manifest = ${row.stateSerManifest}, 
               tag = ${row.tag}, 
               state_timestamp = ${System.currentTimeMillis}
           WHERE persistence_id = ${row.persistenceId} 
             AND sequence_number = ${row.seqNumber} - 1
        """
  }

  private[jdbc] def _getSequenceName() = sequenceNextValUpdater._getSequenceName()

  def _delete(persistenceId: String) = {
    durableStateTable.filter(_.persistenceId === persistenceId).delete
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
}
