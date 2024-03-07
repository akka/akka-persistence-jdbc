package akka.persistence.jdbc.integration

import com.typesafe.config.ConfigFactory
import slick.jdbc.MySQLProfile
import akka.persistence.jdbc.state.scaladsl.DurableStateStorePluginSpec

class MySQLDurableStateStorePluginSpec
    extends DurableStateStorePluginSpec(ConfigFactory.load("mysql-shared-db-application.conf"), MySQLProfile) {}
