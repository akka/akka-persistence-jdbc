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

  override def dropLegacyPKConstraint(): Unit = {
    withStatement(stmt => stmt.execute(s"""ALTER TABLE ${tagTableCfg.tableName} DROP CONSTRAINT "event_tag_pkey""""))
  }

  override def alterEventIdToNullable(): Unit = {
    withStatement(stmt =>
      stmt.execute(
        s"ALTER TABLE ${tagTableCfg.tableName} ALTER COLUMN ${tagTableCfg.columnNames.eventId} DROP NOT NULL"))
  }
}

class MySQLScalaEventByTagMigrationTest extends EventsByTagMigrationTest("mysql-application.conf") with MysqlCleaner {
  override def alterEventIdToNullable(): Unit = {
    withStatement { stmt =>
      stmt.execute(
        s"ALTER TABLE ${tagTableCfg.tableName} MODIFY COLUMN ${tagTableCfg.columnNames.eventId} BIGINT UNSIGNED NULL")
    }
  }

  override def dropLegacyFKConstraint(): Unit = {
    withStatement { stmt =>
      // SELECT AND DROP old FK CONSTRAINT
      val constraintNameQuery =
        s"""
           |SELECT CONSTRAINT_NAME
           |FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
           |WHERE TABLE_NAME = "${tagTableCfg.tableName}" LIMIT 1
                  """.stripMargin
      val resultSet = stmt.executeQuery(constraintNameQuery)
      if (resultSet.next()) {
        val constraintName = resultSet.getString("CONSTRAINT_NAME")
        stmt.execute(s"ALTER TABLE ${tagTableCfg.tableName} DROP FOREIGN KEY $constraintName")
      }
    }
  }

  override def addNewPKConstraint(): Unit = {
    withStatement { stmt =>
      stmt.execute(s"""
                      |ALTER TABLE ${tagTableCfg.tableName}
                      |ADD CONSTRAINT
                      |PRIMARY KEY (${tagTableCfg.columnNames.persistenceId}, ${tagTableCfg.columnNames.sequenceNumber}, ${tagTableCfg.columnNames.tag})
                      """.stripMargin)
    }
  }

  override def addNewFKConstraint(): Unit = {
    withStatement { stmt =>
      stmt.execute(s"""
                      |ALTER TABLE ${tagTableCfg.tableName}
                      |ADD CONSTRAINT fk_event_journal_on_pk
                      |FOREIGN KEY (${tagTableCfg.columnNames.persistenceId}, ${tagTableCfg.columnNames.sequenceNumber})
                      |REFERENCES ${journalTableCfg.tableName} (${journalTableCfg.columnNames.persistenceId}, ${journalTableCfg.columnNames.sequenceNumber})
                      |ON DELETE CASCADE
                      """.stripMargin)
    }
  }
}

class OracleScalaEventByTagMigrationTest
    extends EventsByTagMigrationTest("oracle-application.conf")
    with OracleCleaner {

  override def addNewColumn(): Unit = {
    // mock event_id not null, in order to change it to null later
    withStatement { stmt =>
      stmt.execute(s"ALTER TABLE ${tagTableCfg.tableName} MODIFY ${tagTableCfg.columnNames.eventId} NOT NULL")
    }
  }

  override def alterEventIdToNullable(): Unit = {
    withStatement { stmt =>
      stmt.execute(s"ALTER TABLE ${tagTableCfg.tableName} MODIFY ${tagTableCfg.columnNames.eventId} NULL")
    }
  }

  override def dropLegacyFKConstraint(): Unit = {
    withStatement { stmt =>
      // SELECT AND DROP old FK CONSTRAINT
      val constraintNameQuery =
        s"""
           |SELECT CONSTRAINT_NAME
           |FROM USER_CONSTRAINTS
           |WHERE CONSTRAINT_TYPE = 'R' AND TABLE_NAME = '${tagTableCfg.tableName}'
                  """.stripMargin
      val resultSet = stmt.executeQuery(constraintNameQuery)
      if (resultSet.next()) {
        val constraintName = resultSet.getString("CONSTRAINT_NAME")
        stmt.execute(s"ALTER TABLE ${tagTableCfg.tableName} DROP CONSTRAINT $constraintName")
      }
    }
  }
}

class SqlServerScalaEventByTagMigrationTest
    extends EventsByTagMigrationTest("sqlserver-application.conf")
    with SqlServerCleaner {

  override def dropLegacyPKConstraint(): Unit = {
    withStatement { stmt =>
      // SELECT AND DROP old PK CONSTRAINT
      val constraintNameQuery =
        s"""
           |SELECT *
           |FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
           |WHERE TABLE_NAME = '${tagTableCfg.tableName}' AND CONSTRAINT_TYPE = 'PRIMARY KEY'
                  """.stripMargin
      val resultSet = stmt.executeQuery(constraintNameQuery)
      if (resultSet.next()) {
        val constraintName = resultSet.getString("CONSTRAINT_NAME")
        stmt.execute(s"ALTER TABLE ${tagTableCfg.tableName} DROP CONSTRAINT $constraintName")
      }
    }
  }

  override def addNewPKConstraint(): Unit = {
    // Change new column not null
    withStatement(stmt => {
      stmt.execute(
        s"ALTER TABLE ${tagTableCfg.tableName} ALTER COLUMN ${tagTableCfg.columnNames.persistenceId} NVARCHAR(255) NOT NULL")
      stmt.execute(
        s"ALTER TABLE ${tagTableCfg.tableName} ALTER COLUMN ${tagTableCfg.columnNames.sequenceNumber} NUMERIC(10,0) NOT NULL")
    })
    super.addNewPKConstraint()
  }
}
