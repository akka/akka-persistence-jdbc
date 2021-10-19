package akka.persistence.jdbc.migrator

import akka.pattern.ask
import akka.persistence.jdbc.migrator.JournalMigratorSpec._
import akka.persistence.query.scaladsl.ReadJournal

/*
  val journalConfig = new JournalConfig(config.getConfig("jdbc-journal"))
  val snapshotConfig = new SnapshotConfig(config.getConfig("jdbc-snapshot-store"))
  val readJournalConfig = new ReadJournalConfig(config.getConfig("jdbc-read-journal"))
  val dao = new ByteArrayJournalDao(db, profile, journalConfig, SerializationExtension(system))
 */

abstract class JournalMigratorTest(configName: String) extends JournalMigratorSpec(configName) {

  it should "TODO" in {
    withActorSystem(config) { implicit systemLegacy =>
      withTestActors() { (actorA1, actorA2, actorA3) =>
        withReadJournal { implicit readJournal =>
          (actorA1 ? CreateAccount(1)).futureValue //balance 1
          (actorA2 ? CreateAccount(2)).futureValue //balance 2
          (actorA3 ? CreateAccount(3)).futureValue //balance 3
          (actorA1 ? Deposit(3)).futureValue //balance 4
          (actorA2 ? Deposit(2)).futureValue //balance 4
          (actorA3 ? Deposit(1)).futureValue //balance 4
          (actorA1 ? Withdraw(3)).futureValue //balance 1
          (actorA2 ? Withdraw(2)).futureValue //balance 1
          (actorA3 ? Withdraw(1)).futureValue //balance 1

          eventually {
            (actorA1 ? State).mapTo[BigDecimal].futureValue shouldBe BigDecimal.apply(1)
            (actorA2 ? State).mapTo[BigDecimal].futureValue shouldBe BigDecimal.apply(2)
            (actorA3 ? State).mapTo[BigDecimal].futureValue shouldBe BigDecimal.apply(3)

            countJournal(_ => true).futureValue shouldBe 9
          }
        }
      }
    } // legacy persistence
    withActorSystem(config) { implicit systemNew =>
      // TODO migration
      // val journalOps = new ScalaJdbcReadJournalOperations(systemNew)
      withTestActors() { (actorB1, actorB2, actorB3) =>
        eventually {
          (actorB1 ? State).mapTo[BigDecimal].futureValue shouldBe BigDecimal.apply(1)
          (actorB2 ? State).mapTo[BigDecimal].futureValue shouldBe BigDecimal.apply(2)
          (actorB3 ? State).mapTo[BigDecimal].futureValue shouldBe BigDecimal.apply(3)

          // journalOps.countJournal.futureValue shouldBe 9
        }
      }
    } // new persistence
  }
}

class H2JournalMigratorTest extends JournalMigratorTest("h2-application.conf") with JournalMigratorSpec.H2Cleaner
