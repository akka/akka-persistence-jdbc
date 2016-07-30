/*
 * Copyright 2016 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.jdbc.util

import java.sql.Statement

import akka.actor.ActorSystem
import akka.persistence.jdbc.util.Schema.{ Oracle, SchemaType }
import slick.jdbc.JdbcBackend

object Schema {

  sealed trait SchemaType { def schema: String }
  final case class Postgres(schema: String = "schema/postgres/postgres-schema.sql") extends SchemaType
  final case class H2(schema: String = "schema/h2/h2-schema.sql") extends SchemaType
  final case class MySQL(schema: String = "schema/mysql/mysql-schema.sql") extends SchemaType
  final case class Oracle(schema: String = "schema/oracle/oracle-schema.sql") extends SchemaType
}

trait DropCreate extends ClasspathResources {

  def system: ActorSystem

  def db: JdbcBackend#Database

  val listOfOracleDropQueries = List(
    """DROP TABLE "journal" CASCADE CONSTRAINT""",
    """DROP TABLE "snapshot" CASCADE CONSTRAINT""",
    """DROP TABLE "deleted_to" CASCADE CONSTRAINT""",
    """DROP TRIGGER "ordering_seq_trigger"""",
    """DROP PROCEDURE "reset_sequence"""",
    """DROP SEQUENCE "ordering_seq""""
  )

  def dropOracle(): Unit = withStatement { stmt =>
    listOfOracleDropQueries.foreach { ddl =>
      try stmt.executeUpdate(ddl) catch {
        case t: java.sql.SQLException if t.getMessage contains "ORA-00942" => // suppress known error message in the test
        case t: java.sql.SQLException if t.getMessage contains "ORA-04080" => // suppress known error message in the test
        case t: java.sql.SQLException if t.getMessage contains "ORA-02289" => // suppress known error message in the test
        case t: java.sql.SQLException if t.getMessage contains "ORA-04043" => // suppress known error message in the test
        case t: java.sql.SQLException if t.getMessage contains "ORA-01418" => // suppress known error message in the test
      }
    }
  }

  def dropCreate(schemaType: SchemaType): Unit = schemaType match {
    case Oracle(schema) =>
      dropOracle()
      create(schema, "/")
    case s: SchemaType => create(s.schema)
  }

  def create(schema: String, separator: String = ";"): Unit = for {
    schema <- Option(fromClasspathAsString(schema))
    ddl <- for {
      trimmedLine <- schema.split(separator) map (_.trim)
      if trimmedLine.nonEmpty
    } yield trimmedLine
  } withStatement { stmt =>
    try stmt.executeUpdate(ddl) catch {
      case t: java.sql.SQLSyntaxErrorException if t.getMessage contains "ORA-00942" => // suppress known error message in the test
    }
  }

  def withDatabase[A](f: JdbcBackend#Database => A): A =
    f(db)

  def withSession[A](f: JdbcBackend#Session => A): A = {
    withDatabase { db =>
      val session = db.createSession()
      try f(session) finally session.close()
    }
  }

  def withStatement[A](f: Statement => A): A =
    withSession(session => session.withStatement()(f))
}
