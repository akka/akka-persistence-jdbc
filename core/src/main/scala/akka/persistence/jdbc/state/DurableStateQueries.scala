package akka.persistence.jdbc.state

import slick.jdbc.JdbcProfile
import akka.persistence.jdbc.config.DurableStateTableConfiguration

class DurableStateQueries(val profile: JdbcProfile, override val durableStateTableCfg: DurableStateTableConfiguration)
    extends DurableStateTables {
  import profile.api._

  def _selectByPersistenceId(persistenceId: Rep[String]) =
    durableStateTable.filter(_.persistenceId === persistenceId)

  def _upsertDurableState(row: DurableStateTables.DurableStateRow) =
    durableStateTable.insertOrUpdate(row)

  def _delete(persistenceId: String) = {
    durableStateTable.filter(_.persistenceId === persistenceId).delete
  }
}
