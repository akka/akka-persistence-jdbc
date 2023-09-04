/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.query

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.persistence.jdbc.query.EventsByTagMigrationTest.{ legacyTagKeyConfigOverride, migrationConfigOverride }
import akka.persistence.query.{ EventEnvelope, NoOffset, Sequence }
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

  /**
   * add new column to event_tag table.
   */
  def addNewColumn(): Unit = {}

  /**
   * drop legacy not compatible inserted rows.
   */
  def deleteLegacyRows(): Unit = {
    withStatement { stmt =>
      stmt.execute(s"""
                      |DELETE FROM ${tagTableCfg.tableName}
                      |WHERE ${tagTableCfg.columnNames.persistenceId} IS NULL
                      |AND ${tagTableCfg.columnNames.sequenceNumber} IS NULL
                      |""".stripMargin)
    }
  }

  /**
   * drop old FK constraint
   */
  def dropLegacyFKConstraint(): Unit = {
    withStatement(stmt => stmt.execute(s"""ALTER TABLE ${tagTableCfg.tableName} DROP CONSTRAINT "fk_event_journal""""))

  }

  /**
   * drop old PK  constraint
   */
  def dropLegacyPKConstraint(): Unit = {
    withStatement(stmt => stmt.execute(s"ALTER TABLE ${tagTableCfg.tableName} DROP PRIMARY KEY"))
  }

  /**
   * create new PK constraint for PK column.
   */
  def addNewPKConstraint(): Unit = {
    withStatement { stmt =>
      stmt.execute(s"""
                      |ALTER TABLE ${tagTableCfg.tableName}
                      |ADD CONSTRAINT "pk_event_tag"
                      |PRIMARY KEY (${tagTableCfg.columnNames.persistenceId}, ${tagTableCfg.columnNames.sequenceNumber}, ${tagTableCfg.columnNames.tag})
                      """.stripMargin)
    }
  }

  /**
   * create new FK constraint for PK column.
   */
  def addNewFKConstraint(): Unit = {
    withStatement { stmt =>
      stmt.execute(s"""
                      |ALTER TABLE ${tagTableCfg.tableName}
                      |ADD CONSTRAINT "fk_event_journal_on_pk"
                      |FOREIGN KEY (${tagTableCfg.columnNames.persistenceId}, ${tagTableCfg.columnNames.sequenceNumber})
                      |REFERENCES ${journalTableCfg.tableName} (${journalTableCfg.columnNames.persistenceId}, ${journalTableCfg.columnNames.sequenceNumber})
                      |ON DELETE CASCADE
                      """.stripMargin)
    }
  }

  /**
   * alter the event_id to nullable, so we can skip the InsertAndReturn.
   */
  def alterEventIdToNullable(): Unit = {
    withStatement { stmt =>
      stmt.execute(s"ALTER TABLE ${tagTableCfg.tableName} ALTER COLUMN ${tagTableCfg.columnNames.eventId} BIGINT NULL")
    }
  }

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
    // 1. Mock legacy data on here, but actually using redundant write and read.
    withRollingUpdateActorSystem { implicit system =>
      val journalOps = new ScalaJdbcReadJournalOperations(system)
      withTestActors(replyToMessages = true) { (actor1, actor2, actor3) =>
        (actor1 ? withTags(1, "number")).futureValue
        (actor2 ? withTags(2, "number")).futureValue
        (actor3 ? withTags(3, "number")).futureValue

        journalOps.withEventsByTag()("number", NoOffset) { tp =>
          tp.request(Int.MaxValue)
          tp.expectNext(EventEnvelope(Sequence(1), "my-1", 1, 1, timestamp = 0L))
          tp.expectNext(EventEnvelope(Sequence(2), "my-2", 1, 2, timestamp = 0L))
          tp.expectNext(EventEnvelope(Sequence(3), "my-3", 1, 3, timestamp = 0L))
          tp.cancel()
        }
      }(system)
    }

    // Assume that the user has completed the addition of the new column, then we don't need to maintain
    // the legacy table schema creation.
    addNewColumn();

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

    // 3. Delete the rows inserted in the old way and alter the event_id to nullable so that we can migrate to the read and write from the new PK.
    deleteLegacyRows();
    dropLegacyFKConstraint();
    dropLegacyPKConstraint()
    addNewPKConstraint()
    addNewFKConstraint()
    alterEventIdToNullable();

    // 4. check the migration completed.
    withActorSystem { implicit system =>
      pendingIfOracleWithLegacy()

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

class H2ScalaEventsByTagMigrationTest extends EventsByTagMigrationTest("h2-application.conf") with H2Cleaner
