/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2019 Lightbend Inc. <https://www.lightbend.com>
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

abstract class JdbcSnapshotStoreSpec(config: Config, schemaType: SchemaType)
    extends SnapshotStoreSpec(config)
    with BeforeAndAfterAll
    with ScalaFutures
    with ClasspathResources
    with DropCreate {
  implicit val pc: PatienceConfig = PatienceConfig(timeout = 10.seconds)

  implicit lazy val ec = system.dispatcher

  lazy val cfg = system.settings.config.getConfig("jdbc-journal")

  lazy val journalConfig = new JournalConfig(cfg)

  lazy val db = SlickDatabase.database(cfg, new SlickConfiguration(cfg.getConfig("slick")), "slick.db")

  override def beforeAll(): Unit = {
    dropCreate(schemaType)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    db.close()
  }
}

class PostgresSnapshotStoreSpec
    extends JdbcSnapshotStoreSpec(ConfigFactory.load("postgres-application.conf"), Postgres())

class MySQLSnapshotStoreSpec extends JdbcSnapshotStoreSpec(ConfigFactory.load("mysql-application.conf"), MySQL())

class OracleSnapshotStoreSpec extends JdbcSnapshotStoreSpec(ConfigFactory.load("oracle-application.conf"), Oracle())

class SqlServerSnapshotStoreSpec
    extends JdbcSnapshotStoreSpec(ConfigFactory.load("sqlserver-application.conf"), SqlServer())

class H2SnapshotStoreSpec extends JdbcSnapshotStoreSpec(ConfigFactory.load("h2-application.conf"), H2())
