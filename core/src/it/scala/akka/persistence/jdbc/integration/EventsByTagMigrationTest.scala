/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.integration

import akka.persistence.jdbc.query.{
  EventsByTagMigrationTest,
  MysqlCleaner,
  OracleCleaner,
  PostgresCleaner,
  SqlServerCleaner
}

class PostgresScalaEventsByTagMigrationTest
    extends EventsByTagMigrationTest("postgres-application.conf")
    with PostgresCleaner {

  override def alterEventIdToNullable(): Unit =
    alterColumn(changeToDialect = "DROP NOT NULL")
}

class MySQLScalaEventByTagMigrationTest extends EventsByTagMigrationTest("mysql-application.conf") with MysqlCleaner {
  override def alterEventIdToNullable(): Unit =
    alterColumn(alterDialect = "MODIFY COLUMN", changeToDialect = "BIGINT UNSIGNED NULL")

  override def dropLegacyFKConstraint(): Unit =
    dropConstraint(constraintType = "FOREIGN KEY", constraintDialect = "FOREIGN KEY")

  override def dropLegacyPKConstraint(): Unit =
    dropConstraint(constraintType = "PRIMARY KEY", constraintDialect = "", constraintNameDialect = "KEY")

  override def addNewPKConstraint(): Unit =
    addPKConstraint(constraintNameDialect = "")

  override def addNewFKConstraint(): Unit =
    addFKConstraint()
}

class OracleScalaEventByTagMigrationTest
    extends EventsByTagMigrationTest("oracle-application.conf")
    with OracleCleaner {

  override def addNewColumn(): Unit = {
    // mock event_id not null, in order to change it to null later
    alterColumn(alterDialect = "MODIFY", changeToDialect = "NOT NULL")
  }

  override def alterEventIdToNullable(): Unit =
    alterColumn(alterDialect = "MODIFY", changeToDialect = "NULL")

  override def dropLegacyFKConstraint(): Unit =
    dropConstraint(constraintTableName = "USER_CONSTRAINTS", constraintType = "R")

  override def dropLegacyPKConstraint(): Unit =
    dropConstraint(constraintTableName = "USER_CONSTRAINTS", constraintType = "P")

}

class SqlServerScalaEventByTagMigrationTest
    extends EventsByTagMigrationTest("sqlserver-application.conf")
    with SqlServerCleaner {

  override def addNewPKConstraint(): Unit = {
    // Change new column not null
    alterColumn(columnName = tagTableCfg.columnNames.persistenceId, changeToDialect = "NVARCHAR(255) NOT NULL")
    alterColumn(columnName = tagTableCfg.columnNames.sequenceNumber, changeToDialect = "NUMERIC(10,0) NOT NULL")
    super.addNewPKConstraint()
  }
}
