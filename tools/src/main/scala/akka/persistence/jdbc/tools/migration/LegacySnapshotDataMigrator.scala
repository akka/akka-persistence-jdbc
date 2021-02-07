/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.tools.migration

import akka.actor.ActorSystem
import akka.NotUsed
import akka.persistence.SnapshotMetadata
import akka.stream.scaladsl.Source
import com.typesafe.config.Config

import scala.concurrent.Future

/**
 * This will help migrate the legacy snapshot data onto the new snapshot schema with the
 * appropriate serialization
 *
 * @param config the application config
 * @param system the actor system
 */
case class LegacySnapshotDataMigrator(config: Config)(implicit system: ActorSystem) extends DataMigrator(config) {

  import system.dispatcher

  /**
   * write the latest state snapshot into the new snapshot table applying the proper serialization
   */
  def migrate(): Source[Option[Future[Unit]], NotUsed] = {
    legacyReadJournalDao.allPersistenceIdsSource(Long.MaxValue).mapAsync(1) { persistenceId: String =>
      legacySnapshotDao
        .latestSnapshot(persistenceId)
        .map((o: Option[(SnapshotMetadata, Any)]) => {
          o.map((result: (SnapshotMetadata, Any)) => {
            val (meta, data) = result
            defaultSnapshotDao.save(meta, data)
          })
        })
    }
  }
}
