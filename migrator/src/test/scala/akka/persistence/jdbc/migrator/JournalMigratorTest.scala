package akka.persistence.jdbc.migrator

import akka.pattern.ask
import akka.persistence.jdbc.migrator.JournalMigratorSpec.{ AccountCreated, Deposit, Withdrawn }
import akka.persistence.jdbc.query.ScalaJdbcReadJournalOperations

abstract class JournalMigratorTest(config: String) extends JournalMigratorSpec(config) {

  it should "work in progress" in {
    withActorSystem { implicit system =>
      //val dao = new ByteArrayJournalDao(db, profile, journalConfig, SerializationExtension(system))

      val journalOps = new ScalaJdbcReadJournalOperations(system)
      withTestActors() { (actor1, actor2, actor3) =>
        (actor1 ? AccountCreated(1)).futureValue //balance 1
        (actor2 ? AccountCreated(2)).futureValue //balance 2
        (actor3 ? AccountCreated(3)).futureValue //balance 3
        (actor1 ? Deposit(3)).futureValue //balance 4
        (actor2 ? Deposit(2)).futureValue //balance 4
        (actor3 ? Deposit(1)).futureValue //balance 4
        (actor1 ? Withdrawn(3)).futureValue //balance 3
        (actor2 ? Withdrawn(3)).futureValue //balance 3
        (actor3 ? Withdrawn(3)).futureValue //balance 3

        eventually {
          journalOps.countJournal.futureValue shouldBe 20
        }

        system.terminate()
      }
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
    }
  }

}

class H2JournalMigratorTest extends JournalMigratorTest("h2-application.conf") with JournalMigratorSpec.H2Cleaner
