package akka.persistence.jdbc.state

import slick.jdbc.{ JdbcProfile, SetParameter }
import akka.persistence.jdbc.config.DurableStateTableConfiguration

class DurableStateQueries(val profile: JdbcProfile, override val durableStateTableCfg: DurableStateTableConfiguration)
    extends DurableStateTables {
  import profile.api._

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
  }

  private[jdbc] def _insertDurableState(row: DurableStateTables.DurableStateRow) =
    durableStateTable += row

  /**
   * ; create a sequence
   * CREATE SEQUENCE state_ordering_seq;
   *
   * ; use the sequence for autoinc of id column
   * CREATE TABLE state (
   *   id integer NOT NULL DEFAULT nextval('state_ordering_seq'),
   *   ..
   * );
   *
   * During insert I have no issue, the column `id` gets populated automatically through the sequence
   *
   * ; during update set the id column to the current value of the sequence
   * UPDATE state
   * SET id = (SELECT currval(pg_get_serial_sequence('state', 'id')) + 1)
   * WHERE ...
   *
   * ; change the next value of the sequence so that we don't have a problem with the next insert
   * SELECT setval('state_ordering_seq', select max(id) from state, true)
   */
  private[jdbc] def _updateDurableState(row: DurableStateTables.DurableStateRow, seqName: String) = {
    val sanitized = sanitizeSequenceName(seqName)
    sqlu"""UPDATE state 
           SET global_offset = NEXT VALUE FOR PUBLIC.#${sanitized},
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

  // H2 dependent (https://stackoverflow.com/questions/36244641/h2-equivalent-of-postgres-serial-or-bigserial-column)
  private[jdbc] def _getSequenceName() = {
    sql"""SELECT COLUMN_DEFAULT
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = 'state'
            AND COLUMN_NAME = 'global_offset'
            AND TABLE_SCHEMA = 'PUBLIC'""".as[String]
  }

  // H2 dependent (https://stackoverflow.com/questions/36244641/h2-equivalent-of-postgres-serial-or-bigserial-column)
  private def sanitizeSequenceName(name: String) = name.split('.')(1).stripSuffix(")")

  def _delete(persistenceId: String) = {
    durableStateTable.filter(_.persistenceId === persistenceId).delete
  }
}
