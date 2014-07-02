package akka.persistence.jdbc.util

import scalikejdbc._

trait JdbcInit {
  implicit def session: DBSession

  def createJournalTable(): Unit = sql"""
                           CREATE TABLE IF NOT EXISTS public.journal (
                           persistence_id VARCHAR(255) NOT NULL,
                           sequence_number BIGINT NOT NULL,
                           marker VARCHAR(255) NOT NULL,
                           message TEXT NOT NULL,
                           created TIMESTAMP NOT NULL,
                           PRIMARY KEY(persistence_id, sequence_number))""".update.apply

  def dropJournalTable(): Unit = sql"DROP TABLE IF EXISTS public.journal".update.apply

  def clearJournalTable(): Unit = sql"DELETE FROM public.journal".update.apply

  def createSnapshotTable(): Unit = sql"""
                            CREATE TABLE IF NOT EXISTS public.snapshot (
                            persistence_id VARCHAR(255) NOT NULL,
                            sequence_nr BIGINT NOT NULL,
                            snapshot TEXT NOT NULL,
                            created BIGINT NOT NULL,
                            PRIMARY KEY (persistence_id, sequence_nr))""".update.apply

  def dropSnapshotTable(): Unit = sql"DROP TABLE IF EXISTS public.snapshot".update.apply

  def clearSnapshotTable(): Unit = sql"DELETE FROM public.snapshot".update.apply
}
