package akka.persistence.jdbc.integration

import akka.persistence.jdbc.snapshot.JdbcSnapshotStoreSpec
import akka.persistence.jdbc.util.Schema.{MySQL, Oracle, Postgres, SqlServer}
import com.typesafe.config.ConfigFactory

class PostgresSnapshotStoreSpec
  extends JdbcSnapshotStoreSpec(ConfigFactory.load("postgres-application.conf"), Postgres())

class MySQLSnapshotStoreSpec extends JdbcSnapshotStoreSpec(ConfigFactory.load("mysql-application.conf"), MySQL())

class OracleSnapshotStoreSpec extends JdbcSnapshotStoreSpec(ConfigFactory.load("oracle-application.conf"), Oracle())

class SqlServerSnapshotStoreSpec
  extends JdbcSnapshotStoreSpec(ConfigFactory.load("sqlserver-application.conf"), SqlServer())
