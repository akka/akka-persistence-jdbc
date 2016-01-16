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
import slick.driver.PostgresDriver.api._

object Schema {
  final val Postgres = "schema/postgres/postgres-schema.sql"
  final val MySQL = "schema/mysql/mysql-schema.sql"
  final val Oracle = "schema/oracle/oracle-schema.sql"
}

trait DropCreate extends ClasspathResources {

  def system: ActorSystem

  def dropCreate(schemaName: String): Unit = {
    val schemaFromClasspath = Option(fromClasspathAsString(schemaName))
    if (schemaFromClasspath.isEmpty) println("Could not find schema: " + schemaName)
    else schemaFromClasspath.foreach { schema ⇒
      withStatement { stmt ⇒
        schema.split(";").map(_.trim).filter(_.nonEmpty).foreach { q ⇒
          println(s"[$q]")
          stmt.executeUpdate(q)
        }
      }
    }
  }

  def withSession(f: Session ⇒ Unit): Unit = {
    val session = SlickDatabase(system).db.createSession()
    try f(session) finally session.close()
  }

  def withStatement(f: Statement ⇒ Unit): Unit =
    withSession(session ⇒ session.withStatement()(f(_)))
}
