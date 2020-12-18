/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.testkit.scaladsl

import scala.concurrent.Future

import akka.Done
import akka.actor.ClassicActorSystemProvider
import akka.annotation.ApiMayChange
import akka.persistence.jdbc.testkit.internal.SchemaUtilsImpl
import org.slf4j.LoggerFactory

object SchemaUtils {

  private val logger = LoggerFactory.getLogger("akka.persistence.jdbc.testkit.scaladsl.SchemaUtils")

  /**
   * Drops the schema for both the journal and the snapshot table using the default schema definition.
   *
   * For information about the different schemas and supported databases consult
   * https://doc.akka.io/docs/akka-persistence-jdbc/current/index.html#database-schema
   *
   * This utility method is intended to be used for testing only.
   * For production, it's recommended to run any DDL statements before the system is started.
   *
   * This method will automatically detects the configured database using the settings from `jdbc-journal` config.
   * If configured with `use-shared-db`, it will use the `akka-persistence-jdbc.shared-databases` definition instead.
   * See https://doc.akka.io/docs/akka-persistence-jdbc/current/index.html#sharing-the-database-connection-pool-between-the-journals for details.
   */
  @ApiMayChange
  def dropIfExists()(implicit actorSystem: ClassicActorSystemProvider): Future[Done] =
    SchemaUtilsImpl.dropIfExists(logger)

  /**
   * Creates the schema for both the journal and the snapshot table using the default schema definition.
   *
   * For information about the different schemas and supported databases consult
   * https://doc.akka.io/docs/akka-persistence-jdbc/current/index.html#database-schema
   *
   * This utility method is intended to be used for testing only.
   * For production, it's recommended to run any DDL statements before the system is started.
   *
   * This method will automatically detects the configured database using the settings from `jdbc-journal` config.
   * If configured with `use-shared-db`, it will use the `akka-persistence-jdbc.shared-databases` definition instead.
   * See https://doc.akka.io/docs/akka-persistence-jdbc/current/index.html#sharing-the-database-connection-pool-between-the-journals for details.
   */
  @ApiMayChange
  def createIfNotExists()(implicit actorSystem: ClassicActorSystemProvider): Future[Done] =
    SchemaUtilsImpl.createIfNotExists(logger)

  /**
   * This method can be used to load alternative DDL scripts.
   *
   * This utility method is intended to be used for testing only.
   * For production, it's recommended to run any DDL statements before the system is started.
   *
   * It will use the database settings found under `jdbc-journal`, or `akka-persistence-jdbc.shared-databases` if configured so.
   * See https://doc.akka.io/docs/akka-persistence-jdbc/current/index.html#sharing-the-database-connection-pool-between-the-journals for details.
   *
   * @param script the DDL script. The passed script can contain more then one SQL statements separated by a ; (semi-colon).
   */
  @ApiMayChange
  def applyScript(script: String)(implicit actorSystem: ClassicActorSystemProvider): Future[Done] =
    applyScript(script, separator = ";", configKey = "jdbc-journal")

  /**
   * This method can be used to load alternative DDL scripts.
   *
   * This utility method is intended to be used for testing only.
   * For production, it's recommended to create the table with DDL statements before the system is started.
   *
   * It will use the database settings found under `configKey`, or `akka-persistence-jdbc.shared-databases` if configured so.
   * See https://doc.akka.io/docs/akka-persistence-jdbc/current/index.html#sharing-the-database-connection-pool-between-the-journals for details.
   *
   * @param script the DDL script. The passed `script` can contain more then one SQL statements.
   * @param separator used to separate the different DDL statements.
   * @param configKey the database configuration key to use. Can be `jbdc-journal` or `jdbc-snapshot-store`.
   */
  @ApiMayChange
  def applyScript(script: String, separator: String, configKey: String)(
      implicit actorSystem: ClassicActorSystemProvider): Future[Done] =
    SchemaUtilsImpl.applyScript(script, separator, configKey, logger)

}
