package akka.persistence.jdbc.integration

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.persistence.jdbc.state.scaladsl.JdbcDurableStateSpec
import akka.persistence.jdbc.testkit.internal.Postgres

class PostgresScalaJdbcDurableStateStoreQueryTest 
  extends JdbcDurableStateSpec(ConfigFactory.load("postgres-shared-db-application.conf"), Postgres) {
  implicit lazy val system: ActorSystem =
    ActorSystem("JdbcDurableStateSpec", config.withFallback(customSerializers))
}