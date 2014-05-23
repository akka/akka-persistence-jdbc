package akka.persistence.jdbc.common

import org.scalatest.{BeforeAndAfterAll, Matchers, FlatSpec}
import akka.persistence.jdbc.util.JdbcInit
import akka.persistence.jdbc.journal.RowTypeMarkers._
import akka.persistence.postgresql.common.PostgresqlConfig

class JdbcConnectionTest extends FlatSpec with Matchers with BeforeAndAfterAll with PostgresqlConfig with JdbcConnection with JdbcInit {

  "Insert a record" should "have a new record" in {
    executeUpdate(
      s"""INSERT INTO public.event_store (processor_id, sequence_number, marker, message, created)
         | VALUES ('abcdefg', 1, '$AcceptedMarker', 'abcdefg', current_timestamp)""".stripMargin) match {
      case Left(msg) => fail(msg.toString())
      case Right(numRecords) => assert(numRecords === 1)
    }

    withResultSet("SELECT count(*) FROM event_store") { rs =>
      assert(rs.next())
      assert(rs.getInt(1) === 1)
    } match {
      case Left(msg) => fail(msg.toString())
      case _ =>
    }
  }

  override protected def beforeAll(): Unit = {
    dropTable()
    createTable()
    clearTable()
  }

  override protected def afterAll(): Unit = {
  }

}
