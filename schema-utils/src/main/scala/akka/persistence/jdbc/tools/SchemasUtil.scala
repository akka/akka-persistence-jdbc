/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.tools

import com.typesafe.config.Config
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.api.output.MigrateResult

import scala.jdk.CollectionConverters.{ CollectionHasAsScala, MapHasAsJava }

/**
 * executes the database migration
 *
 * @param config the application config
 */
final case class SchemasUtil(config: Config) {
  private val userKey: String = "jdbc-journal.slick.db.user"
  private val passwordKey: String = "jdbc-journal.slick.db.password"
  private val urlKey: String = "jdbc-journal.slick.db.url"

  // Flyway placeholders values keys
  private val eventJournalPlaceholderValueKey: String = "jdbc-journal.tables.event_journal.tableName"
  private val eventTagPlaceholderValueKey: String = "jdbc-journal.tables.event_tag.tableName"
  private val stateSnapshotPlaceholderValueKey: String = "jdbc-snapshot-store.tables.snapshot.tableName"

  // Flyway placeholders
  private val eventJournalPlaceholder = "apj:event_journal"
  private val eventTagPlaceholder: String = "apj:event_tag"
  private val stateSnapshotPlaceholder: String = "apj:state_snapshot"

  private val url: String = config.getString(urlKey)
  private val user: String = config.getString(userKey)
  private val password: String = config.getString(passwordKey)

  /**
   * create the persistence schemas.
   *
   * @return the list of migration versions run
   */
  def createIfNotExists(): Seq[String] = {
    val flywayConfig: FluentConfiguration = Flyway.configure
      .dataSource(url, user, password)
      .table("apj_schema_history")
      .locations(new Location("classpath:db/migration/postgres"))
      .ignoreMissingMigrations(true) // in case someone has some missing migrations
      .placeholders(Map(
        eventJournalPlaceholder -> config.getString(eventJournalPlaceholderValueKey),
        eventTagPlaceholder -> config.getString(eventTagPlaceholderValueKey),
        stateSnapshotPlaceholder -> config.getString(stateSnapshotPlaceholderValueKey)).asJava)

    val flyway: Flyway = flywayConfig.load
    flyway.baseline()

    // running the migration
    val result: MigrateResult = flyway.migrate()

    // let us return the sequence of migration versions applied sorted in a descending order
    result.migrations.asScala.toSeq.map(_.version).sortWith(_ > _)
  }
}
