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

package akka.persistence.jdbc.snapshot

import akka.persistence.jdbc.config._
import akka.persistence.jdbc.util.Schema._
import akka.persistence.jdbc.util.{ ClasspathResources, DropCreate, SlickDatabase }
import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._

abstract class JdbcSnapshotStoreSpec(config: Config) extends SnapshotStoreSpec(config) with BeforeAndAfterAll with ScalaFutures with ClasspathResources with DropCreate {

  implicit val pc: PatienceConfig = PatienceConfig(timeout = 10.seconds)

  implicit val ec = system.dispatcher

  val cfg = system.settings.config.getConfig("jdbc-journal")

  val journalConfig = new JournalConfig(cfg)

  val db = SlickDatabase.forConfig(cfg, journalConfig.slickConfiguration)
}

class PostgresSnapshotStoreSpec extends JdbcSnapshotStoreSpec(ConfigFactory.load("postgres-application.conf")) {
  dropCreate(Postgres())
}

class MySQLSnapshotStoreSpec extends JdbcSnapshotStoreSpec(ConfigFactory.load("mysql-application.conf")) {
  dropCreate(MySQL())
}

class OracleSnapshotStoreSpec extends JdbcSnapshotStoreSpec(ConfigFactory.load("oracle-application.conf")) {
  dropCreate(Oracle())
}