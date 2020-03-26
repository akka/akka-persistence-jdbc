/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.query

import java.util.UUID

import akka.Done
import akka.actor.ActorSystem
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.persistence.jdbc.journal.dao.{ ByteArrayJournalDao, JournalTables }
import akka.serialization.SerializationExtension
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import com.typesafe.config.{ ConfigValue, ConfigValueFactory }
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.slf4j.LoggerFactory

import scala.collection.immutable
import scala.concurrent.{ Await, ExecutionContextExecutor, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Random, Success }

object JournalDaoStreamMessagesMemoryTest {

  val configOverrides: Map[String, ConfigValue] = Map("jdbc-journal.fetch-size" -> ConfigValueFactory.fromAnyRef("100"))
}

abstract class JournalDaoStreamMessagesMemoryTest(configFile: String)
    extends QueryTestSpec(configFile, JournalDaoStreamMessagesMemoryTest.configOverrides)
    with JournalTables {
  private val log = LoggerFactory.getLogger(this.getClass)

  val journalSequenceActorConfig = readJournalConfig.journalSequenceRetrievalConfiguration
  val journalTableCfg = journalConfig.journalTableConfiguration

  import profile.api._

  implicit val askTimeout = 50.millis

  def generateId: Int = 0

  behavior.of("Replaying Persistence Actor")

  it should "stream events" in {
    withActorSystem { implicit system: ActorSystem =>
      withDatabase { db =>
        val maxMem = Runtime.getRuntime.maxMemory()

        // We don't want it to be too large otherwise the tests take too long to setup
        // we also must be able to call .toInt on maxMen (see below) and we need it to stay inside the Int boundaries
        // Moreover, the Oracle docker is limited to the size of the file on disk and we can't push too much data on it
        // (128M seems to work for the Oracle Docker)
        val memoryLimit = 128000000
        if (maxMem > memoryLimit) {
          info(
            s"JournalDaoStreamMessagesMemoryTest can only be run with a limited amount of memory ($memoryLimit), found [$maxMem]")
          pending
        }

        implicit val mat: ActorMaterializer = ActorMaterializer()
        implicit val ec: ExecutionContextExecutor = system.dispatcher

        val persistenceId = UUID.randomUUID().toString
        val dao = new ByteArrayJournalDao(db, profile, journalConfig, SerializationExtension(system))

        val payloadSize = 5000 // 5000 bytes
        val eventsPerBatch = 1000

        val numberOfInsertBatches = {
          // calculate the number of batches using a factor to make sure we go a little bit over the limit
          (maxMem.toInt / (payloadSize * eventsPerBatch) * 1.2).round.toInt
        }
        val totalMessages = numberOfInsertBatches * eventsPerBatch
        val totalMessagePayload = totalMessages * payloadSize
        log.info(
          s"maxMem: $maxMem, batches: $numberOfInsertBatches (with $eventsPerBatch events), total messages: $totalMessages, total msgs size: $totalMessagePayload")

        // payload can be the same when inserting to avoid unnecessary memory usage
        val payload = Array.fill(payloadSize)('a'.toByte)

        val lastInsert =
          Source
            .fromIterator(() => (1 to numberOfInsertBatches).toIterator)
            .mapAsync(1) { i =>
              val end = i * eventsPerBatch
              val start = end - (eventsPerBatch - 1)
              log.info(s"batch $i - events from $start to $end")
              val atomicWrites =
                (start to end).map { j =>
                  AtomicWrite(immutable.Seq(PersistentRepr(payload, j, persistenceId)))
                }.toSeq

              dao.asyncWriteMessages(atomicWrites).map(_ => i)
            }
            .runWith(Sink.last)

        // wait until we write all messages
        // being very generous, 1 second per message
        lastInsert.futureValue(Timeout(totalMessages.seconds))

        log.info("Events written, starting replay")

        val messagesSrc =
          dao.messagesWithBatch(persistenceId, 0, totalMessages, batchSize = 100, None)
        val doneFut =
          messagesSrc.runForeach {
            case Success(repr) =>
              if (repr.sequenceNr % 100 == 0)
                log.info(s"fetched: ${repr.persistenceId} - ${repr.sequenceNr}/${totalMessages}")
            case Failure(exception) => println(exception)
          }

        // wait until we read all messages
        // being very generous, 1 second per message
        doneFut.futureValue(Timeout(totalMessages.seconds))
      }
    }
  }
}

class PostgresJournalDaoStreamMessagesMemoryTest
    extends JournalDaoStreamMessagesMemoryTest("postgres-application.conf")
    with PostgresCleaner

class MySQLJournalDaoStreamMessagesMemoryTest
    extends JournalDaoStreamMessagesMemoryTest("mysql-application.conf")
    with MysqlCleaner

class OracleJournalDaoStreamMessagesMemoryTest
    extends JournalDaoStreamMessagesMemoryTest("oracle-application.conf")
    with OracleCleaner

class SqlServerJournalDaoStreamMessagesMemoryTest
    extends JournalDaoStreamMessagesMemoryTest("sqlserver-application.conf")
    with SqlServerCleaner
