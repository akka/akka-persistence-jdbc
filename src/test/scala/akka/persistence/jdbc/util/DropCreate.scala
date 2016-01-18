/*
 * Copyright 2015 Dennis Vriend
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
import akka.persistence.jdbc.util.Schema.{ Oracle, Postgres, SchemaType }
import slick.driver.PostgresDriver.api._

import scala.util.Try

object Schema {
  sealed trait SchemaType { def schema: String }
  final case class Postgres(val schema: String = "schema/postgres/postgres-schema.sql") extends SchemaType
  final case class MySQL(val schema: String = "schema/mysql/mysql-schema.sql") extends SchemaType
  final case class Oracle(val schema: String = "schema/oracle/oracle-schema.sql") extends SchemaType
}

trait DropCreate extends ClasspathResources {

  def system: ActorSystem

  def dropCreate(schemaType: SchemaType) = schemaType match {
    case Oracle(schema) ⇒
      withStatement { stmt ⇒
        Try {
          stmt.executeUpdate("DROP TABLE public.journal CASCADE CONSTRAINT")
          stmt.executeUpdate("DROP TABLE public.deleted_to CASCADE CONSTRAINT")
          stmt.executeUpdate("DROP TABLE public.snapshot CASCADE CONSTRAINT")
        } recover {
          case t: Throwable ⇒
            t.printStackTrace()
            create(schema)
        }
      }
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
    stmt.executeUpdate(query)
  }

  def withSession(f: Session ⇒ Unit): Unit = {
    val session = SlickDatabase(system).db.createSession()
    try f(session) finally session.close()
  }

  def withStatement(f: Statement ⇒ Unit): Unit =
    withSession(session ⇒ session.withStatement()(f))
}
