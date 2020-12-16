/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.util

import java.sql.Statement

import akka.actor.ActorSystem
import akka.persistence.jdbc.util.Schema.{ Oracle, SchemaType }
import com.typesafe.config.Config
import slick.jdbc.JdbcBackend.{ Database, Session }

object Schema {
  sealed trait SchemaType {
    def legacySchema: String
    def schema: String
  }
  final case class Postgres(
      legacySchema: String = "schema/postgres/postgres-schema.sql",
      schema: String = "schema/postgres/postgres-schema-v5.sql")
      extends SchemaType
  final case class H2(
      legacySchema: String = "schema/postgres/h2-schema.sql",
      schema: String = "schema/h2/h2-schema-v5.sql")
      extends SchemaType
  final case class MySQL(
      legacySchema: String = "schema/mysql/mysql-schema.sql",
      schema: String = "schema/mysql/mysql-schema-v5.sql")
      extends SchemaType
  final case class Oracle(
      legacySchema: String = "schema/mysql/oracle-schema.sql",
      schema: String = "schema/oracle/oracle-schema-v5.sql")
      extends SchemaType
  final case class SqlServer(
      legacySchema: String = "schema/sqlserver/sqlserver-schema.sql",
      schema: String = "schema/sqlserver/sqlserver-schema-v5.sql")
      extends SchemaType
}

trait DropCreate extends ClasspathResources {
  def db: Database
  def config: Config

  def newDao =
    config.getString("jdbc-journal.dao") == "akka.persistence.jdbc.journal.dao.AkkaSerializerJournalDao"

  val listOfOracleDropQueries = List(
    """ALTER SESSION SET ddl_lock_timeout = 15""", // (ddl lock timeout in seconds) this allows tests which are still writing to the db to finish gracefully
    """DROP TABLE event_journal CASCADE CONSTRAINT""",
    """DROP TABLE journal CASCADE CONSTRAINT""",
    """DROP TABLE event_tag CASCADE CONSTRAINT""",
    """DROP TABLE SNAPSHOT CASCADE CONSTRAINT""",
    """DROP SEQUENCE event_journal__ordering_seq""",
    """DROP TRIGGER event_journal__ordering_trg """)

  def dropOracle(): Unit =
    withStatement { stmt =>
      listOfOracleDropQueries.foreach { ddl =>
        try stmt.executeUpdate(ddl)
        catch {
          case t: java.sql.SQLException if t.getMessage contains "ORA-00942" => // suppress known error message in the test
          case t: java.sql.SQLException if t.getMessage contains "ORA-04080" => // suppress known error message in the test
          case t: java.sql.SQLException if t.getMessage contains "ORA-02289" => // suppress known error message in the test
          case t: java.sql.SQLException if t.getMessage contains "ORA-04043" => // suppress known error message in the test
          case t: java.sql.SQLException if t.getMessage contains "ORA-01418" => // suppress known error message in the test
        }
      }
    }

  def dropCreate(schemaType: SchemaType): Unit =
    schemaType match {
      case Oracle(legacy, schema) =>
        dropOracle()
        create(if (newDao) schema else legacy, "/")
      case s: SchemaType => create(if (newDao) s.schema else s.legacySchema)
    }

  def create(schema: String, separator: String = ";"): Unit = {
    for {
      schema <- Option(fromClasspathAsString(schema))
      ddl <- for {
        trimmedLine <- schema.split(separator).map(_.trim)
        if trimmedLine.nonEmpty
      } yield trimmedLine
    } withStatement { stmt =>
      try stmt.executeUpdate(ddl)
      catch {
        case t: java.sql.SQLSyntaxErrorException if t.getMessage contains "ORA-00942" =>
        // suppress known error message in the test
      }
    }
  }

  def withDatabase[A](f: Database => A): A =
    f(db)

  def withSession[A](f: Session => A): A = {
    withDatabase { db =>
      val session = db.createSession()
      try f(session)
      finally session.close()
    }
  }

  def withStatement[A](f: Statement => A): A =
    withSession(session => session.withStatement()(f))
}
