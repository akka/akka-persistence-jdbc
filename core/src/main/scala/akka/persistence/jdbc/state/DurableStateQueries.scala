package akka.persistence.jdbc.state

import slick.jdbc.JdbcProfile
import akka.persistence.jdbc.config.DurableStateTableConfiguration

class DurableStateQueries(val profile: JdbcProfile, override val durableStateTableCfg: DurableStateTableConfiguration)
    extends DurableStateTables {
  import profile.api._

  def _selectByPersistenceId(persistenceId: Rep[String]) =
    durableStateTable.filter(_.persistenceId === persistenceId)

  def _selectByTag(tag: Rep[Option[String]], offset: Option[Long]) = {
    offset
      .map { o =>
        durableStateTable.filter(r => r.tag === tag && r.ordering > o)
      }
      .getOrElse {
        durableStateTable.filter(r => r.tag === tag)
      }
  }

  def _insertDurableState(row: DurableStateTables.DurableStateRow) =
    durableStateTable += row

  def _updateDurableState(row: DurableStateTables.DurableStateRow) = {
    durableStateTable
      .filter(r =>
        r.persistenceId === row.persistenceId &&
        r.seqNumber === row.seqNumber - 1)
      .map(r => (r.statePayload, r.stateSerId, r.stateSerManifest, r.tag, r.stateTimestamp))
      .update((row.statePayload, row.stateSerId, row.stateSerManifest, row.tag, System.currentTimeMillis()))
  }

  def _delete(persistenceId: String) = {
    durableStateTable.filter(_.persistenceId === persistenceId).delete
  }
}
