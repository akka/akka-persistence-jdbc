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
import akka.persistence.jdbc.extension.SlickDatabase
import akka.persistence.jdbc.util.Schema.{ Oracle, SchemaType }
import slick.driver.PostgresDriver.api._

import scala.util.{ Failure, Try }

object Schema {
  sealed trait SchemaType { def schema: String }
  final case class Postgres(val schema: String = "schema/postgres/postgres-schema.sql") extends SchemaType
  final case class MySQL(val schema: String = "schema/mysql/mysql-schema.sql") extends SchemaType
  final case class Oracle(val schema: String = "schema/oracle/oracle-schema.sql") extends SchemaType
  final case class H2(val schema: String = "schema/mysql/mysql-schema.sql") extends SchemaType
}

trait DropCreate extends ClasspathResources {

  def system: ActorSystem

  val oracleQueries = List(
    """DROP TABLE "journal" CASCADE CONSTRAINT""",
    """DROP TABLE "deleted_to" CASCADE CONSTRAINT""",
    """DROP TABLE "snapshot" CASCADE CONSTRAINT"""
  )

  def dropCreate(schemaType: SchemaType) = schemaType match {
    case Oracle(schema) ⇒
      withStatement { stmt ⇒
        for {
          ddl ← oracleQueries
          result ← Try(stmt.executeUpdate(ddl)) recoverWith {
            case t: Throwable ⇒
              t.printStackTrace()
              Failure(t)
          }
        } ()
      }
      create(schema)
    case s: SchemaType ⇒ create(s.schema)
  }

  def create(schema: String) = for {
    schema ← Option(fromClasspathAsString(schema))
    query ← for {
      trimmedLine ← schema.split(";").map(_.trim)
      if trimmedLine.nonEmpty
    } yield trimmedLine
  } withStatement { stmt ⇒
    println(query)
    Try(stmt.executeUpdate(query)).recover {
      case t: Throwable ⇒
        t.printStackTrace()
    }
  }

  def withDatabase[A](f: Database ⇒ A): A =
    f(SlickDatabase(system).db)

  def withSession[A](f: Session ⇒ A): A = {
    withDatabase { db ⇒
      val session = db.createSession()
      try f(session) finally session.close()
    }
  }

  def withStatement[A](f: Statement ⇒ A): A =
    withSession(session ⇒ session.withStatement()(f))
}
