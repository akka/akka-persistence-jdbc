/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.integration

import akka.persistence.jdbc.cleanup.scaladsl.EventSourcedCleanupTest
import akka.persistence.jdbc.query.{ MysqlCleaner, OracleCleaner, PostgresCleaner, SqlServerCleaner }

// Note: these tests use the shared-db configs, the test for all (so not only current) events use the regular db config

class PostgresEventSourcedCleanupTest
    extends EventSourcedCleanupTest("postgres-shared-db-application.conf")
    with PostgresCleaner

class MySQLEventSourcedCleanupTest extends EventSourcedCleanupTest("mysql-shared-db-application.conf") with MysqlCleaner

class OracleEventSourcedCleanupTest
    extends EventSourcedCleanupTest("oracle-shared-db-application.conf")
    with OracleCleaner

class SqlServerEventSourcedCleanupTest
    extends EventSourcedCleanupTest("sqlserver-shared-db-application.conf")
    with SqlServerCleaner
