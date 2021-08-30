package akka.persistence.jdbc.integration

import com.typesafe.config.ConfigFactory
import slick.jdbc.PostgresProfile
import akka.persistence.jdbc.state.scaladsl.DurableStateStorePluginSpec

class PostgresDurableStateStorePluginSpec 
  extends DurableStateStorePluginSpec(ConfigFactory.load("postgres-shared-db-application.conf"), PostgresProfile) {
}
