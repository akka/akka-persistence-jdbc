package akka.persistence.jdbc.integration

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.persistence.jdbc.state.scaladsl.JdbcDurableStateSpec
import akka.persistence.jdbc.testkit.internal.MySQL
import org.scalatest.Ignore

// FIXME this test doesn't pass because of something with SequenceNextValUpdater
@Ignore
class MySQLScalaJdbcDurableStateStoreQueryTest
    extends JdbcDurableStateSpec(ConfigFactory.load("mysql-shared-db-application.conf"), MySQL) {
  implicit lazy val system: ActorSystem =
    ActorSystem("JdbcDurableStateSpec", config.withFallback(customSerializers))

}
