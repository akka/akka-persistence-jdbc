package akka.persistence.jdbc.migrator

import akka.pattern.ask
import akka.persistence.jdbc.migrator.JournalMigratorSpec._
import akka.persistence.jdbc.query.ScalaJdbcReadJournalOperations

/*
  val journalConfig = new JournalConfig(config.getConfig("jdbc-journal"))
  val snapshotConfig = new SnapshotConfig(config.getConfig("jdbc-snapshot-store"))
  val readJournalConfig = new ReadJournalConfig(config.getConfig("jdbc-read-journal"))
 */

/*
    //val dao = new ByteArrayJournalDao(db, profile, journalConfig, SerializationExtension(system))

    /**/
    /* withActorSystem { implicit system =>
       val migrate = JournalMigrator(SlickDatabase.profile(cfg, "slick")).migrate()
         eventually {
           journalOps.countJournal.futureValue shouldBe 9
         }
         (actor1 ? AccountOpenedCommand(1)).futureValue
         (actor2 ? AccountOpenedCommand(2)).futureValue
         (actor3 ? AccountOpenedCommand(3)).futureValue
         assert(1 == 1)
         system.terminate()
       }*/
 */
abstract class JournalMigratorTest(config: String) extends JournalMigratorSpec(config) {

  it should "TODO" in {
    withActorSystem { implicit system =>
      val journalOps = new ScalaJdbcReadJournalOperations(system)
      withTestActors() { (actor1, actor2, actor3) =>
        (actor1 ? CreateAccount(1)).futureValue //balance 1
        (actor2 ? CreateAccount(2)).futureValue //balance 2
        (actor3 ? CreateAccount(3)).futureValue //balance 3

        (actor1 ? Deposit(3)).futureValue //balance 4
        (actor2 ? Deposit(2)).futureValue //balance 4
        (actor3 ? Deposit(1)).futureValue //balance 4

        (actor1 ? Withdraw(3)).futureValue //balance 1
        (actor2 ? Withdraw(2)).futureValue //balance 1
        (actor3 ? Withdraw(1)).futureValue //balance 1

        eventually {

          (actor1 ? State).mapTo[BigDecimal].futureValue shouldBe BigDecimal.apply(1)
          (actor2 ? State).mapTo[BigDecimal].futureValue shouldBe BigDecimal.apply(2)
          (actor3 ? State).mapTo[BigDecimal].futureValue shouldBe BigDecimal.apply(3)

          // journalOps.countJournal.futureValue shouldBe 9
        }
      }
    }
  }
}

class H2JournalMigratorTest extends JournalMigratorTest("h2-application.conf") with JournalMigratorSpec.H2Cleaner
