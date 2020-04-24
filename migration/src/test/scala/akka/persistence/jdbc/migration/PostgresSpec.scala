/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.migration

import java.sql.{ Connection, DriverManager }
import java.util.Properties

import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.BeforeAndAfterAll
import org.testcontainers.containers.PostgreSQLContainer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PostgresSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val postgres: PostgreSQLContainer[_] = {
    val c = new PostgreSQLContainer()
    c.withDatabaseName("public")
    c.withInitScript("postgres/init.sql")
    c
  }
  var migrationConfig: Config = null
  val connectionProperties = new Properties()

  override def beforeAll(): Unit = {
    postgres.start()
    migrationConfig = ConfigFactory.parseString(s"""migration {
         |database-vendor = postgres
         |url = "${postgres.getJdbcUrl}"
         |user = "${postgres.getUsername}"
         |password = "${postgres.getPassword}"
         |}""".stripMargin).getConfig("migration")

    connectionProperties.put("user", postgres.getUsername);
    connectionProperties.put("password", postgres.getPassword);
  }

  override def afterAll(): Unit = {
    postgres.stop()
  }

  "Migration 002" should "be applied" in {
    Main.run(migrationConfig)
    val connection = DriverManager.getConnection(postgres.getJdbcUrl, connectionProperties);
    println(existingTables(connection))
    val stmt = connection.createStatement()
    stmt.executeQuery("SELECT * FROM migrated2;")
  }

  "Scala migration 003" should "be applied" in {
    Main.run(migrationConfig)
    val connection = DriverManager.getConnection(postgres.getJdbcUrl, connectionProperties);
    println(existingTables(connection))
    val stmt = connection.createStatement()
    val rs = stmt.executeQuery("SELECT * FROM test_user;")
    val sb = new StringBuilder()
    while (rs.next()) {
      sb.append(rs.getString(1)).append("\n")
    }
    sb.toString() shouldBe "Obelix\n"
  }

  private def existingTables(connection: Connection) = {
    val stmt = connection.createStatement()
    val rs = stmt.executeQuery(
      "SELECT schemaname, tablename FROM pg_catalog.pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema');")
    val sb = new StringBuilder("Existing tables:\n")
    while (rs.next()) {
      sb.append(" " + rs.getString(1) + "." + rs.getString(2) + "\n")
    }
    sb.toString()
  }
}
