/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.snapshot.dao

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.annotation.InternalApi
import akka.persistence.jdbc.config.SnapshotConfig
import akka.persistence.jdbc.db.SlickDatabase
import akka.serialization.{ Serialization, SerializationExtension }
import akka.stream.Materializer
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

@InternalApi
private[jdbc] object SnapshotDaoInstantiation {

  def snapshotDao(
      snapshotConfig: SnapshotConfig,
      slickDb: SlickDatabase)(implicit system: ActorSystem, ec: ExecutionContext, mat: Materializer): SnapshotDao = {
    val fqcn = snapshotConfig.pluginConfig.dao
    val profile: JdbcProfile = slickDb.profile
    val args = Seq(
      (classOf[Database], slickDb.database),
      (classOf[JdbcProfile], profile),
      (classOf[SnapshotConfig], snapshotConfig),
      (classOf[Serialization], SerializationExtension(system)),
      (classOf[ExecutionContext], ec),
      (classOf[Materializer], mat))
    system.asInstanceOf[ExtendedActorSystem].dynamicAccess.createInstanceFor[SnapshotDao](fqcn, args) match {
      case Success(dao)   => dao
      case Failure(cause) => throw cause
    }
  }

}
