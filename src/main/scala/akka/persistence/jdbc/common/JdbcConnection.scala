package akka.persistence.jdbc.common

import javax.sql.DataSource
import org.apache.commons.dbcp.BasicDataSource
import java.sql.{ResultSet, Statement, Connection}

trait JdbcConnection extends Config {

  lazy val dataSource: DataSource = {
    val ds = new BasicDataSource
    ds.setDriverClassName(driverClassName)
    ds.setUrl(url)
    ds.setUsername(username)
    ds.setPassword(password)
    ds.setMaxActive(maxActive)
    ds.setMaxIdle(maxIdle)
    ds.setInitialSize(initialSize)
    ds
  }

  def withConnection[R](f: Connection => R): Either[Seq[Throwable], R] = {
    import resource._
    val result = managed[Connection](dataSource.getConnection) map {(conn:Connection) => f(conn) }
    result.either
  }

  def withStatement[R](f: Statement => R): Either[Seq[Throwable], R] = {
    withConnection[R] { conn =>
      f(conn.createStatement)
    }
  }

  def withResultSet[R](query: String)(f: ResultSet => R): Either[Seq[Throwable], R] = {
    withStatement[R] { statement =>
      f(statement.executeQuery(query))
    }
  }

  def executeUpdate(query: String): Either[Seq[Throwable], Int] = {
    withStatement[Int] { statement =>
      statement.executeUpdate(query)
    }
  }

  def execute(query: String): Either[Seq[Throwable], Boolean] = {
    withStatement[Boolean] { statement =>
      statement.execute(query)
    }
  }

}
