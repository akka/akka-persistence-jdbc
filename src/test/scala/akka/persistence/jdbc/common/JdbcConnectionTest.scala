package akka.persistence.jdbc.common

import akka.actor.ActorSystem
import akka.persistence.jdbc.journal.RowTypeMarkers._
import akka.persistence.jdbc.util.{H2JdbcInit, JdbcInit, PostgresqlJdbcInit}
import akka.testkit.TestKit
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike}
import scalikejdbc._

abstract class JdbcConnectionTest extends TestKit(ActorSystem("test")) with FlatSpecLike with BeforeAndAfterAll with ScalikeConnection with JdbcInit {

  "Insert a record" should "have a new record" in {
      sql"""INSERT INTO journal
              (persistence_id, sequence_number, marker, message, created)
            VALUES ('abcdefg', 1, ${AcceptedMarker}, 'abcdefg', current_timestamp)""".update.apply

      sql"SELECT count(*) FROM journal".map(_.long(1)).single.apply match {
        case Some(1) =>
        case msg @ _ => fail("Number of records is:" + msg)
      }
  }

  override def pluginConfig: PluginConfig = PluginConfig(system)

  override protected def beforeAll(): Unit = {
    dropJournalTable()
    createJournalTable()
  }
}

class PostgresqlConnectionTest extends JdbcConnectionTest with PostgresqlJdbcInit

class H2ConnectionTest extends JdbcConnectionTest with H2JdbcInit
