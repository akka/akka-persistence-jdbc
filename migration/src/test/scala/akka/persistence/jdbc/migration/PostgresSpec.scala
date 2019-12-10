package akka.persistence.jdbc.migration

import java.sql.{Connection, DriverManager}
import java.util.Properties

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.testcontainers.containers.PostgreSQLContainer

class PostgresSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  val postgres: PostgreSQLContainer[_] = new PostgreSQLContainer().withInitScript("postgres/init.sql")
  var migrationConfig: Config = null
  val connectionProperties = new Properties()

  override def beforeAll(): Unit = {
    postgres.start()
    migrationConfig = ConfigFactory.parseString(
      s"""migration {
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
    stmt.executeQuery("SELECT * FROM public.migrated2;")
  }

  private def existingTables(connection: Connection) = {
    val stmt = connection.createStatement()
    val rs = stmt.executeQuery("SELECT schemaname, tablename FROM pg_catalog.pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema');")
    val sb = new StringBuilder("Existing tables:\n")
    while (rs.next()) {
      sb.append(" " + rs.getString(1) + "." + rs.getString(2) + "\n")
    }
    sb.toString()
  }
}
