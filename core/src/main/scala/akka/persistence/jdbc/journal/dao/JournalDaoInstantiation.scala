/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2025 Lightbend Inc. <https://akka.io>
 */

package akka.persistence.jdbc.journal.dao

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.annotation.InternalApi
import akka.persistence.jdbc.config.JournalConfig
import akka.persistence.jdbc.db.SlickDatabase
import akka.serialization.{ Serialization, SerializationExtension }
import akka.stream.Materializer
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

@InternalApi
private[jdbc] object JournalDaoInstantiation {

  def journalDao(
      journalConfig: JournalConfig,
      slickDb: SlickDatabase)(implicit system: ActorSystem, ec: ExecutionContext, mat: Materializer): JournalDao = {
    val fqcn = journalConfig.pluginConfig.dao
    val profile: JdbcProfile = slickDb.profile
    val args = Seq(
      (classOf[Database], slickDb.database),
      (classOf[JdbcProfile], profile),
      (classOf[JournalConfig], journalConfig),
      (classOf[Serialization], SerializationExtension(system)),
      (classOf[ExecutionContext], ec),
      (classOf[Materializer], mat))
    system.asInstanceOf[ExtendedActorSystem].dynamicAccess.createInstanceFor[JournalDao](fqcn, args) match {
      case Success(dao)   => dao
      case Failure(cause) => throw cause
    }
  }

}
