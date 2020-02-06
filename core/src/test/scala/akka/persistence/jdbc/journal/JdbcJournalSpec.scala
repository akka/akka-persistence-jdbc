/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.journal

import akka.persistence.CapabilityFlag
import akka.persistence.jdbc.config._
import akka.persistence.jdbc.util.Schema._
import akka.persistence.jdbc.util.{ ClasspathResources, DropCreate }
import akka.persistence.jdbc.db.{ SlickDatabase, SlickExtension }
import akka.persistence.journal.JournalSpec
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }

import scala.concurrent.duration._

abstract class JdbcJournalSpec(config: Config, schemaType: SchemaType)
    extends JournalSpec(config)
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with ScalaFutures
    with ClasspathResources
    with DropCreate {
  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = true

  implicit val pc: PatienceConfig = PatienceConfig(timeout = 10.seconds)

  implicit lazy val ec = system.dispatcher

  lazy val cfg = system.settings.config.getConfig("jdbc-journal")

  lazy val journalConfig = new JournalConfig(cfg)

  lazy val db = SlickExtension(system).database(cfg).database

  override def beforeAll(): Unit = {
    dropCreate(schemaType)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    db.close()
    super.afterAll()
  }
}

class PostgresJournalSpec extends JdbcJournalSpec(ConfigFactory.load("postgres-application.conf"), Postgres())
class PostgresJournalSpecSharedDb
    extends JdbcJournalSpec(ConfigFactory.load("postgres-shared-db-application.conf"), Postgres())
class PostgresJournalSpecPhysicalDelete
    extends JdbcJournalSpec(
      ConfigFactory
        .load("postgres-application.conf")
        .withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false)),
      Postgres())

class MySQLJournalSpec extends JdbcJournalSpec(ConfigFactory.load("mysql-application.conf"), MySQL())
class MySQLJournalSpecSharedDb extends JdbcJournalSpec(ConfigFactory.load("mysql-shared-db-application.conf"), MySQL())
class MySQLJournalSpecPhysicalDelete
    extends JdbcJournalSpec(
      ConfigFactory
        .load("mysql-application.conf")
        .withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false)),
      MySQL())

class OracleJournalSpec extends JdbcJournalSpec(ConfigFactory.load("oracle-application.conf"), Oracle())
class OracleJournalSpecSharedDb
    extends JdbcJournalSpec(ConfigFactory.load("oracle-shared-db-application.conf"), Oracle())
class OracleJournalSpecPhysicalDelete
    extends JdbcJournalSpec(
      ConfigFactory
        .load("oracle-application.conf")
        .withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false)),
      Oracle())

class SqlServerJournalSpec extends JdbcJournalSpec(ConfigFactory.load("sqlserver-application.conf"), SqlServer())
class SqlServerJournalSpecSharedDb
    extends JdbcJournalSpec(ConfigFactory.load("sqlserver-shared-db-application.conf"), SqlServer())
class SqlServerJournalSpecPhysicalDelete
    extends JdbcJournalSpec(
      ConfigFactory
        .load("sqlserver-application.conf")
        .withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false)),
      SqlServer())

class H2JournalSpec extends JdbcJournalSpec(ConfigFactory.load("h2-application.conf"), H2())
class H2JournalSpecSharedDb extends JdbcJournalSpec(ConfigFactory.load("h2-shared-db-application.conf"), H2())
class H2JournalSpecPhysicalDelete
    extends JdbcJournalSpec(
      ConfigFactory
        .load("h2-application.conf")
        .withValue("jdbc-journal.logicalDelete", ConfigValueFactory.fromAnyRef(false)),
      H2())
