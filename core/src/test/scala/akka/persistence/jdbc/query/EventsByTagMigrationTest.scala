/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.query

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.persistence.jdbc.query.EventsByTagMigrationTest.{ legacyTagKeyConfigOverride, migrationConfigOverride }
import akka.persistence.query.{ EventEnvelope, Sequence }
import com.typesafe.config.{ ConfigFactory, ConfigValue, ConfigValueFactory }

import scala.concurrent.duration._

object EventsByTagMigrationTest {
  val maxBufferSize = 20
  val refreshInterval = 500.milliseconds
  val legacyTagKey = true

  val legacyTagKeyConfigOverride: Map[String, ConfigValue] = Map(
    "jdbc-read-journal.max-buffer-size" -> ConfigValueFactory.fromAnyRef(maxBufferSize.toString),
    "jdbc-read-journal.refresh-interval" -> ConfigValueFactory.fromAnyRef(refreshInterval.toString),
    "jdbc-journal.tables.event_tag.legacy-tag-key" -> ConfigValueFactory.fromAnyRef(legacyTagKey))

  val migrationConfigOverride: Map[String, ConfigValue] = Map(
    "jdbc-read-journal.max-buffer-size" -> ConfigValueFactory.fromAnyRef(maxBufferSize.toString),
    "jdbc-read-journal.refresh-interval" -> ConfigValueFactory.fromAnyRef(refreshInterval.toString))
}

abstract class EventsByTagMigrationTest(config: String) extends QueryTestSpec(config, migrationConfigOverride) {
  final val NoMsgTime: FiniteDuration = 100.millis

  val tagTableCfg = journalConfig.eventTagTableConfiguration
  val journalTableCfg = journalConfig.eventJournalTableConfiguration
  val joinSQL: String =
    s"JOIN ${journalTableName} ON ${tagTableCfg.tableName}.${tagTableCfg.columnNames.eventId} = ${journalTableName}.${journalTableCfg.columnNames.ordering}"
  val fromSQL: String =
    s"FROM ${journalTableName} WHERE ${tagTableCfg.tableName}.${tagTableCfg.columnNames.eventId} = ${journalTableName}.${journalTableCfg.columnNames.ordering}"

  def dropConstraint(
      tableName: String = tagTableCfg.tableName,
      constraintTableName: String = "INFORMATION_SCHEMA.TABLE_CONSTRAINTS",
      constraintType: String,
      constraintDialect: String = "CONSTRAINT",
      constraintNameDialect: String = ""): Unit = {
    withStatement { stmt =>
      // SELECT AND DROP old CONSTRAINT
      val constraintNameQuery =
        s"""
           |SELECT CONSTRAINT_NAME
           |FROM $constraintTableName
           |WHERE TABLE_NAME = '$tableName' AND CONSTRAINT_TYPE = '$constraintType'
                  """.stripMargin
      val resultSet = stmt.executeQuery(constraintNameQuery)
      if (resultSet.next()) {
        val constraintName = resultSet.getString("CONSTRAINT_NAME")
        stmt.execute(s"ALTER TABLE $tableName DROP $constraintDialect $constraintName $constraintNameDialect")
      }
    }
  }

  def addPKConstraint(
      tableName: String = tagTableCfg.tableName,
      pidColumnName: String = tagTableCfg.columnNames.persistenceId,
      seqNrColumnName: String = tagTableCfg.columnNames.sequenceNumber,
      tagColumnName: String = tagTableCfg.columnNames.tag,
      constraintNameDialect: String = "pk_event_tag"): Unit = {
    withStatement { stmt =>
      stmt.execute(s"""
           |ALTER TABLE $tableName
           |ADD CONSTRAINT $constraintNameDialect
           |PRIMARY KEY ($pidColumnName, $seqNrColumnName, $tagColumnName)
                      """.stripMargin)
    }
  }

  def addFKConstraint(
      tableName: String = tagTableCfg.tableName,
      pidColumnName: String = tagTableCfg.columnNames.persistenceId,
      seqNrColumnName: String = tagTableCfg.columnNames.sequenceNumber,
      journalTableName: String = journalTableCfg.tableName,
      journalPidColumnName: String = tagTableCfg.columnNames.persistenceId,
      journalSeqNrColumnName: String = tagTableCfg.columnNames.sequenceNumber,
      constraintNameDialect: String = "fk_event_journal_on_pk"): Unit = {
    withStatement { stmt =>
      stmt.execute(s"""
                      |ALTER TABLE $tableName
                      |ADD CONSTRAINT $constraintNameDialect
                      |FOREIGN KEY ($pidColumnName, $seqNrColumnName)
                      |REFERENCES $journalTableName ($journalPidColumnName, $journalSeqNrColumnName)
                      |ON DELETE CASCADE
                      """.stripMargin)
    }
  }

  def alterColumn(
      tableName: String = tagTableCfg.tableName,
      alterDialect: String = "ALTER COLUMN",
      columnName: String = tagTableCfg.columnNames.eventId,
      changeToDialect: String = "BIGINT NULL"): Unit = {
    withStatement { stmt =>
      stmt.execute(s"ALTER TABLE $tableName $alterDialect $columnName $changeToDialect")
    }
  }

  def fillNewColumn(
      joinDialect: String = "",
      pidSetDialect: String =
        s"${tagTableCfg.columnNames.persistenceId} = ${journalTableName}.${journalTableCfg.columnNames.persistenceId}",
      seqNrSetDialect: String =
        s"${tagTableCfg.columnNames.sequenceNumber} = ${journalTableName}.${journalTableCfg.columnNames.sequenceNumber}",
      fromDialect: String = ""): Unit = {
    withStatement { stmt =>
      stmt.execute(s"""
                      |UPDATE ${tagTableCfg.tableName} ${joinDialect}
                      |SET ${pidSetDialect},
                      |${seqNrSetDialect}
                      |${fromDialect}""".stripMargin)
    }
  }

  /**
   * add new column to event_tag table.
   */
  def addNewColumn(): Unit = {}

  /**
   * fill new column for exists rows.
   */
  def migrateLegacyRows(): Unit = {
    fillNewColumn(fromDialect = fromSQL);
  }

  /**
   * drop old FK constraint
   */
  def dropLegacyFKConstraint(): Unit =
    dropConstraint(constraintType = "FOREIGN KEY")

  /**
   * drop old PK  constraint
   */
  def dropLegacyPKConstraint(): Unit =
    dropConstraint(constraintType = "PRIMARY KEY")

  /**
   * create new PK constraint for PK column.
   */
  def addNewPKConstraint(): Unit =
    addPKConstraint()

  /**
   * create new FK constraint for PK column.
   */
  def addNewFKConstraint(): Unit =
    addFKConstraint()

  // override this, so we can reset the value.
  def withRollingUpdateActorSystem(f: ActorSystem => Unit): Unit = {
    val legacyTagKeyConfig = legacyTagKeyConfigOverride.foldLeft(ConfigFactory.load(config)) {
      case (conf, (path, configValue)) =>
        conf.withValue(path, configValue)
    }

    implicit val system: ActorSystem = ActorSystem("migrator-test", legacyTagKeyConfig)
    f(system)
    system.terminate().futureValue
  }

  it should "migrate event tag to new way" in {
    // 1. Mock legacy tag column on here, but actually using new tag write.
    withRollingUpdateActorSystem { implicit system =>

      val journalOps = new ScalaJdbcReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
        (actor1 ? withTags(1, "number")).futureValue
        (actor2 ? withTags(2, "number")).futureValue
        (actor3 ? withTags(3, "number")).futureValue

        journalOps.withEventsByTag()("number", Sequence(Long.MinValue)) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1, timestamp = 0L))
          tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, 2, timestamp = 0L))
          tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 3, timestamp = 0L))
          tp.cancel()
        }
      }(system)
    }

    // Assume that the user could alter table for the addition of the new column manually, then we don't need to maintain
    // the legacy table schema creation.
    if (newDao) {
      addNewColumn();
      migrateLegacyRows();
    }

    // 2. write and read redundancy
    withRollingUpdateActorSystem { implicit system =>
      val journalOps = new ScalaJdbcReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
        (actor1 ? withTags(4, "number")).futureValue
        (actor2 ? withTags(5, "number")).futureValue
        (actor3 ? withTags(6, "number")).futureValue
        // Delay events that have not yet been projected can still be read.
        journalOps.withEventsByTag()("number", Sequence(Long.MinValue)) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1, timestamp = 0L))
          tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, 2, timestamp = 0L))
          tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 3, timestamp = 0L))
          tp.expectNext(EventEnvelope(Sequence(4), "my-1", 2, 4, timestamp = 0L))
          tp.expectNext(EventEnvelope(Sequence(5), "my-2", 2, 5, timestamp = 0L))
          tp.expectNext(EventEnvelope(Sequence(6), "my-3", 2, 6, timestamp = 0L))
          tp.cancel()
        }
      }(system)
    }

    // 3. Migrate the old constraints so that we can change read and write from the new PK.
    if (newDao) {
      dropLegacyFKConstraint();
      dropLegacyPKConstraint()
      addNewPKConstraint()
      addNewFKConstraint()
    }

    // 4. check the migration completed.
    withActorSystem { implicit system =>

      val journalOps = new ScalaJdbcReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>

        (actor1 ? withTags(7, "number")).futureValue
        (actor2 ? withTags(8, "number")).futureValue
        (actor3 ? withTags(9, "number")).futureValue

        journalOps.withEventsByTag()("number", Sequence(3)) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNext(EventEnvelope(Sequence(4), "my-1", 2, 4, timestamp = 0L))
          tp.expectNext(EventEnvelope(Sequence(5), "my-2", 2, 5, timestamp = 0L))
          tp.expectNext(EventEnvelope(Sequence(6), "my-3", 2, 6, timestamp = 0L))
          tp.expectNext(EventEnvelope(Sequence(7), "my-1", 3, 7, timestamp = 0L))
          tp.expectNext(EventEnvelope(Sequence(8), "my-2", 3, 8, timestamp = 0L))
          tp.expectNext(EventEnvelope(Sequence(9), "my-3", 3, 9, timestamp = 0L))
          tp.cancel()
        }

      }(system)
    }
  }
}

class H2ScalaEventsByTagMigrationTest extends EventsByTagMigrationTest("h2-application.conf") with H2Cleaner {

  override def migrateLegacyRows(): Unit = {
    fillNewColumn(
      pidSetDialect = s"""${tagTableCfg.columnNames.persistenceId} = (
           |    SELECT ${journalTableCfg.columnNames.persistenceId}
           |    ${fromSQL}
           |)""".stripMargin,
      seqNrSetDialect = s"""${tagTableCfg.columnNames.sequenceNumber} = (
           |    SELECT ${journalTableCfg.columnNames.sequenceNumber}
           |    ${fromSQL}
           |)""".stripMargin)
  }
}
