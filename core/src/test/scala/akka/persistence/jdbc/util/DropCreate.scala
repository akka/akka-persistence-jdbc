/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.util

import java.sql.Statement

import akka.annotation.InternalApi
import akka.persistence.jdbc.testkit.internal.SchemaType
import akka.persistence.jdbc.testkit.internal.SchemaUtilsImpl
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.JdbcBackend.Session

/**
 * INTERNAL API
 */
@InternalApi
private[jdbc] trait DropCreate {

  private val logger = LoggerFactory.getLogger(this.getClass)
  def db: Database

  /**
   * INTERNAL API
   */
  @InternalApi
  private[jdbc] def dropAndCreate(schemaType: SchemaType): Unit = {
    // blocking calls, usually done in our before test methods
    SchemaUtilsImpl.dropWithSlick(schemaType, logger, db)
    SchemaUtilsImpl.createWithSlick(schemaType, logger, db)
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  private[jdbc] def withDatabase[A](f: Database => A): A =
    f(db)
}
