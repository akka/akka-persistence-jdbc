package akka.persistence.jdbc.migration

import com.typesafe.config.{Config, ConfigFactory}
import org.flywaydb.core.Flyway

object Main extends App {

  val config = ConfigFactory.load().getConfig("akka-persistence-jdbc.migration")

  def run(config: Config): Unit = {
    val url = config.getString("url")
    val user = config.getString("user")
    val password = config.getString("password")

    val flyway = Flyway.configure.dataSource(url, user, password)
      .table("apjdbc_schema_history").load
    flyway.baseline()
    flyway.migrate()


  }
}
