package akka.persistence.jdbc.util

import akka.persistence.jdbc.common.JdbcConnection

trait JdbcInit { this: JdbcConnection =>

  val createTableQuery = """CREATE TABLE IF NOT EXISTS public.event_store (
                           |  processor_id VARCHAR(255) NOT NULL,
                           |  sequence_number BIGINT NOT NULL,
                           |  marker VARCHAR(255) NOT NULL,
                           |  message TEXT NOT NULL,
                           |  created TIMESTAMP NOT NULL,
                           |  PRIMARY KEY(processor_id, sequence_number)
                           |)""".stripMargin

  val dropTableQuery = "DROP TABLE IF EXISTS public.event_store"

  val clearTableQuery = "DELETE FROM public.event_store"

  def createTable(): Unit =
    withStatement { statement =>
      statement.executeUpdate(createTableQuery)
    } match {
      case Left(errors) => println("create errors: " + errors.toString())
      case _ =>
    }


  def clearTable(): Unit =
    withStatement { statement =>
      statement.executeUpdate(clearTableQuery)
    } match {
      case Left(errors) => println("clear errors: " + errors.toString())
      case _ =>
    }

  def dropTable(): Unit =
    withStatement { statement =>
      statement.executeUpdate(dropTableQuery)
    } match {
      case Left(errors) => println("drop errors: " + errors.toString())
      case _ =>
    }
}
