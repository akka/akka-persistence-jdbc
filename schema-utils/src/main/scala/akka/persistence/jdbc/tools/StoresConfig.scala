package akka.persistence.jdbc.tools

import akka.persistence.jdbc.config.{ JournalConfig, ReadJournalConfig, SnapshotConfig }
import com.typesafe.config.Config

final case class StoresConfig(config: Config) {
  val journalConfig = new JournalConfig(config.getConfig("jdbc-journal"))
  val snapshotConfig = new SnapshotConfig(config.getConfig("jdbc-snapshot-store"))
  val readJournalConfig = new ReadJournalConfig(config.getConfig("jdbc-read-journal"))
}
