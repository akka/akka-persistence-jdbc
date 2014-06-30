package akka.persistence.jdbc.util

import scalikejdbc._

trait JdbcInit {
  implicit def session: DBSession

  def createTable(): Unit = sql"""CREATE TABLE IF NOT EXISTS public.event_store (
                           processor_id VARCHAR(255) NOT NULL,
                           sequence_number BIGINT NOT NULL,
                           marker VARCHAR(255) NOT NULL,
                           message TEXT NOT NULL,
                           created TIMESTAMP NOT NULL,
                           PRIMARY KEY(processor_id, sequence_number)
                           )""".update.apply

  def dropTable(): Unit = sql"DROP TABLE IF EXISTS public.event_store".update.apply

  def clearTable(): Unit = sql"DELETE FROM public.event_store".update.apply
}
