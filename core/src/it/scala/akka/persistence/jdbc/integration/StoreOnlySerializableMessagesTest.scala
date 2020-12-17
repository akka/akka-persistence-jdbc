package akka.persistence.jdbc.integration

import akka.persistence.jdbc.serialization.StoreOnlySerializableMessagesTest
import akka.persistence.jdbc.util.Schema.{MySQL, Oracle, Postgres, SqlServer}

class PostgresStoreOnlySerializableMessagesTest
  extends StoreOnlySerializableMessagesTest("postgres-application.conf", Postgres())

class MySQLStoreOnlySerializableMessagesTest
  extends StoreOnlySerializableMessagesTest("mysql-application.conf", MySQL())

class OracleStoreOnlySerializableMessagesTest
  extends StoreOnlySerializableMessagesTest("oracle-application.conf", Oracle())

class SqlServerStoreOnlySerializableMessagesTest
  extends StoreOnlySerializableMessagesTest("sqlserver-application.conf", SqlServer())
