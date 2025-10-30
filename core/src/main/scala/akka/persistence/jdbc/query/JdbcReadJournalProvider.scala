/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2025 Lightbend Inc. <https://akka.io>
 */

package akka.persistence.jdbc.query

import akka.actor.ExtendedActorSystem
import akka.persistence.query.ReadJournalProvider
import com.typesafe.config.Config

class JdbcReadJournalProvider(system: ExtendedActorSystem, config: Config, configPath: String)
    extends ReadJournalProvider {
  override def scaladslReadJournal(): scaladsl.JdbcReadJournal =
    new scaladsl.JdbcReadJournal(config, configPath)(system)

  override def javadslReadJournal(): javadsl.JdbcReadJournal = new javadsl.JdbcReadJournal(scaladslReadJournal())
}
