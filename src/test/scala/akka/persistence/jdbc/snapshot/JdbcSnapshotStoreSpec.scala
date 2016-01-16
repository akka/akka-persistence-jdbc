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

package akka.persistence.jdbc.snapshot

import akka.persistence.jdbc.dao.Tables
import akka.persistence.jdbc.extension.{ AkkaPersistenceConfig, SlickDatabase }
import akka.persistence.jdbc.util.{ ClasspathResources, DropCreate, Schema, SlickDriver }
import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import slick.driver.JdbcProfile

import scala.concurrent.duration._

abstract class JdbcSnapshotStoreSpec(config: Config) extends SnapshotStoreSpec(config) with BeforeAndAfterAll with ScalaFutures with Tables with ClasspathResources with DropCreate {

  implicit val pc: PatienceConfig = PatienceConfig(timeout = 10.seconds)

  implicit val ec = system.dispatcher

  val db = SlickDatabase(system).db

  override val profile: JdbcProfile =
    SlickDriver.forDriverName(AkkaPersistenceConfig(system).slickDriver)
}

class PostgresSnapshotStoreSpec extends JdbcSnapshotStoreSpec(ConfigFactory.load("postgres-application.conf")) {
  dropCreate(Schema.Postgres)
}

class MySQLSnapshotStoreSpec extends JdbcSnapshotStoreSpec(ConfigFactory.load("mysql-application.conf")) {
  dropCreate(Schema.MySQL)
}

//class OracleSnapshotStoreSpec extends JdbcSnapshotStoreSpec(ConfigFactory.load("oracle-application.conf")) {
//  dropCreate(Schema.Oracle)
//}

