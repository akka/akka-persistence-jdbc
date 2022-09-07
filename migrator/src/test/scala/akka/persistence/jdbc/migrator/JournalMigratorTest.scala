/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.migrator

import akka.Done
import akka.pattern.ask
import akka.persistence.jdbc.db.SlickDatabase
import akka.persistence.jdbc.migrator.MigratorSpec._

abstract class JournalMigratorTest(configName: String) extends MigratorSpec(configName) {

  it should "migrate the event journal" in {
    withLegacyActorSystem { implicit systemLegacy =>
      withReadJournal { implicit readJournal =>
        withTestActors() { (actorA1, actorA2, actorA3) =>
          eventually {
            countJournal().futureValue shouldBe 0
            (actorA1 ? CreateAccount(1)).futureValue // balance 1
            (actorA2 ? CreateAccount(2)).futureValue // balance 2
            (actorA3 ? CreateAccount(3)).futureValue // balance 3
            (actorA1 ? Deposit(3)).futureValue // balance 4
            (actorA2 ? Deposit(2)).futureValue // balance 4
            (actorA3 ? Deposit(1)).futureValue // balance 4
            (actorA1 ? Withdraw(3)).futureValue // balance 1
            (actorA2 ? Withdraw(2)).futureValue // balance 1
            (actorA3 ? Withdraw(1)).futureValue // balance 1
            (actorA1 ? State).mapTo[Int].futureValue shouldBe 1
            (actorA2 ? State).mapTo[Int].futureValue shouldBe 2
            (actorA3 ? State).mapTo[Int].futureValue shouldBe 3
            countJournal().futureValue shouldBe 9
          }
        }
      }
    } // legacy persistence
    withActorSystem { implicit systemNew =>
      withReadJournal { implicit readJournal =>
        eventually {
          countJournal().futureValue shouldBe 0 // before migration
          JournalMigrator(SlickDatabase.profile(config, "slick")).migrate().futureValue shouldBe Done
          countJournal().futureValue shouldBe 9 // after migration
        }
        withTestActors() { (actorB1, actorB2, actorB3) =>
          eventually {
            (actorB1 ? State).mapTo[Int].futureValue shouldBe 1
            (actorB2 ? State).mapTo[Int].futureValue shouldBe 2
            (actorB3 ? State).mapTo[Int].futureValue shouldBe 3
          }
        }
      }
    } // new persistence
  }

  it should "migrate the event journal preserving the order of events" in {
    withLegacyActorSystem { implicit systemLegacy =>
      withReadJournal { implicit readJournal =>
        withTestActors() { (actorA1, actorA2, actorA3) =>
          (actorA1 ? CreateAccount(0)).futureValue
          (actorA2 ? CreateAccount(0)).futureValue
          (actorA3 ? CreateAccount(0)).futureValue
          for (i <- 1 to 999) {
            (actorA1 ? Deposit(i)).futureValue
            (actorA2 ? Deposit(i)).futureValue
            (actorA3 ? Deposit(i)).futureValue
          }
          eventually {
            countJournal().futureValue shouldBe 3000
          }
        }
      }
    } // legacy persistence
    withActorSystem { implicit systemNew =>
      withReadJournal { implicit readJournal =>
        eventually {
          countJournal().futureValue shouldBe 0 // before migration
          JournalMigrator(SlickDatabase.profile(config, "slick")).migrate().futureValue shouldBe Done
          countJournal().futureValue shouldBe 3000 // after migration
          val allEvents: Seq[Seq[AccountEvent]] = events().futureValue
          allEvents.size shouldBe 3
          val seq1: Seq[Int] = allEvents.head.map(_.amount)
          val seq2: Seq[Int] = allEvents(1).map(_.amount)
          val seq3: Seq[Int] = allEvents(2).map(_.amount)
          val expectedResult: Seq[Int] = 0 to 999
          seq1 shouldBe expectedResult
          seq2 shouldBe expectedResult
          seq3 shouldBe expectedResult
        }
      }
    } // new persistence
  }

  it should "migrate the event journal preserving tags" in {
    withLegacyActorSystem { implicit systemLegacy =>
      withReadJournal { implicit readJournal =>
        withTestActors() { (actorA1, actorA2, actorA3) =>
          (actorA1 ? CreateAccount(0)).futureValue
          (actorA2 ? CreateAccount(0)).futureValue
          (actorA3 ? CreateAccount(0)).futureValue
          for (i <- 1 to 999) {
            (actorA1 ? Deposit(i)).futureValue
            (actorA2 ? Deposit(i)).futureValue
            (actorA3 ? Deposit(i)).futureValue
          }
          eventually {
            countJournal().futureValue shouldBe 3000
          }
        }
      }
    } // legacy persistence
    withActorSystem { implicit systemNew =>
      withReadJournal { implicit readJournal =>
        eventually {
          countJournal().futureValue shouldBe 0 // before migration
          JournalMigrator(SlickDatabase.profile(config, "slick")).migrate().futureValue shouldBe Done
          countJournal().futureValue shouldBe 3000 // after migration
          val evenEvents: Seq[AccountEvent] = eventsByTag(MigratorSpec.Even).futureValue
          evenEvents.size shouldBe 1500
          evenEvents.forall(e => e.amount % 2 == 0) shouldBe true

          val oddEvents: Seq[AccountEvent] = eventsByTag(MigratorSpec.Odd).futureValue
          oddEvents.size shouldBe 1500
          oddEvents.forall(e => e.amount % 2 == 1) shouldBe true
        }
      }
    } // new persistence
  }
}

class H2JournalMigratorTest extends JournalMigratorTest("h2-application.conf") with MigratorSpec.H2Cleaner
