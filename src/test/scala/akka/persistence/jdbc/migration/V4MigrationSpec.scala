package akka.persistence.jdbc.migration

import java.io.{File, FileOutputStream}
import java.nio.file.Files

import akka.actor.{ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.jdbc.SimpleSpec
import akka.persistence.jdbc.config.JournalConfig
import akka.persistence.jdbc.util.{ClasspathResources, DropCreate, SlickDatabase}
import com.typesafe.config.ConfigFactory
import org.h2.util.IOUtils
import org.scalatest.BeforeAndAfter
import slick.jdbc.JdbcBackend.Database
import akka.pattern.ask
import akka.persistence.jdbc.migraition.V4JournalMigration
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._

class V4MigrationSpec extends SimpleSpec with BeforeAndAfter with ClasspathResources {

  var system: ActorSystem = _
  var tmpDirectory: File = _

  before {
    tmpDirectory = Files.createTempDirectory("akka-persistence-jdbc-migration-").toFile
    IOUtils.copyAndClose(fromClasspathAsStream("v3-h2-data.mv.db"), new FileOutputStream(new File(tmpDirectory, "database.mv.db")))
    applySchemaMigration()
  }

  after {
    if (system != null) {
      system.terminate()
    }
    def delete(file: File): Unit = {
      if (file.isDirectory) {
        file.listFiles().foreach(delete)
      }
      file.delete()
    }
    if (tmpDirectory.exists()) {
      delete(tmpDirectory)
    }
  }

  "JournalTable" should "support loading a V3 events after schema migration" in {
    system = ActorSystem("test", backwardsCompatiblilityConfig())
    // JDBC journal should read old data.
    Await.result(system.actorOf(Props(new TestActor)) ? "load", 10.seconds) shouldBe "abcdef"
  }

  it should "support loading a V3 events after event migration" in {
    new V4JournalMigration(backwardsCompatiblilityConfig().getConfig("jdbc-journal"), system).run()
    system = ActorSystem("test", loadConfig(""))
    // JDBC journal is not configured to use old data, so will only read new data, assuming its migrated.
    Await.result(system.actorOf(Props(new TestActor)) ? "load", 10.seconds) shouldBe "abcdef"
  }

  implicit val timeout = Timeout(10.seconds)

  class TestActor extends PersistentActor {
    var data: String = ""
    override def receiveRecover = {
      case s: String => data += s
    }
    override def receiveCommand = {
      case "load" => sender() ! data
    }
    override def persistenceId = "some-id"
  }

  private def applySchemaMigration(): Unit = {
    withDb { database =>
      new DropCreate {
        override def db = database
      }.create("schema/h2/h2-migration-v4.sql")
    }
  }

  private def withDb(f: Database => Unit) = {
    val config = loadConfig("").getConfig("jdbc-journal")
    val db = SlickDatabase.forConfig(config, new JournalConfig(config).slickConfiguration)
    try {
      f(db)
    } finally {
      db.close()
    }
  }

  private def backwardsCompatiblilityConfig() = loadConfig("akka-persistence-jdbc.migration.v4.backwards-compatibility = true");

  private def loadConfig(overrides: String) = {
    ConfigFactory.parseString(overrides)
      .withFallback(ConfigFactory.parseString(s"""slick.db.url = "jdbc:h2:${new File(tmpDirectory, "database").getCanonicalPath}""""))
      .withFallback(ConfigFactory.parseResources("h2-application.conf"))
      .withFallback(ConfigFactory.parseResources("reference.conf"))
      .resolve()
  }
}
