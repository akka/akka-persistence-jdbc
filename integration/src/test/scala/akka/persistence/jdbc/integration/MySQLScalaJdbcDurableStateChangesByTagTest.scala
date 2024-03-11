package akka.persistence.jdbc.integration

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.persistence.jdbc.state.scaladsl.JdbcDurableStateSpec
import akka.persistence.jdbc.testkit.internal.Mysql

class MySQLScalaJdbcDurableStateStoreQueryTest
    extends JdbcDurableStateSpec(ConfigFactory.load("mysql-shared-db-application.conf"), MySQL) {
  implicit lazy val system: ActorSystem =
    ActorSystem("JdbcDurableStateSpec", config.withFallback(customSerializers))
}
