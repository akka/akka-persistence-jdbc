/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2025 Lightbend Inc. <https://akka.io>
 */

package akka.persistence.jdbc.query

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.persistence.jdbc.config.JournalConfig
import akka.persistence.jdbc.journal.dao.JournalDao
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.serialization.{ Serialization, SerializationExtension }
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.{ Materializer, SystemMaterializer }
import com.typesafe.config.{ ConfigValue, ConfigValueFactory }
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.slf4j.LoggerFactory
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.JdbcProfile

import java.lang.management.{ ManagementFactory, MemoryMXBean }
import java.util.UUID
import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }
import scala.util.{ Failure, Success }

object JournalDaoStreamMessagesMemoryTest {

  val configOverrides: Map[String, ConfigValue] = Map("jdbc-journal.fetch-size" -> ConfigValueFactory.fromAnyRef("100"))

  val MB = 1024 * 1024
}

abstract class JournalDaoStreamMessagesMemoryTest(configFile: String)
    extends QueryTestSpec(configFile, JournalDaoStreamMessagesMemoryTest.configOverrides) {
  import JournalDaoStreamMessagesMemoryTest.MB

  private val log = LoggerFactory.getLogger(this.getClass)

  val memoryMBean: MemoryMXBean = ManagementFactory.getMemoryMXBean

  it should "stream events" in {
    withActorSystem { implicit system: ActorSystem =>
      withDatabase { db =>
        implicit val ec: ExecutionContextExecutor = system.dispatcher
        implicit val mat: Materializer = SystemMaterializer(system).materializer

        val persistenceId = UUID.randomUUID().toString
        val writerUuid = UUID.randomUUID().toString
        val fqcn = journalConfig.pluginConfig.dao
        val args = Seq(
          (classOf[Database], db),
          (classOf[JdbcProfile], profile),
          (classOf[JournalConfig], journalConfig),
          (classOf[Serialization], SerializationExtension(system)),
          (classOf[ExecutionContext], ec),
          (classOf[Materializer], mat))
        val dao: JournalDao =
          system.asInstanceOf[ExtendedActorSystem].dynamicAccess.createInstanceFor[JournalDao](fqcn, args) match {
            case Success(dao)   => dao
            case Failure(cause) => throw cause
          }

        val payloadSize = 5000 // 5000 bytes
        val eventsPerBatch = 1000

        val maxMem = 64 * MB

        val numberOfInsertBatches = {
          // calculate the number of batches using a factor to make sure we go a little bit over the limit
          (maxMem / (payloadSize * eventsPerBatch) * 1.2).round.toInt
        }
        val totalMessages = numberOfInsertBatches * eventsPerBatch
        val totalMessagePayload = totalMessages * payloadSize
        log.info(
          s"batches: $numberOfInsertBatches (with $eventsPerBatch events), total messages: $totalMessages, total msgs size: $totalMessagePayload")

        // payload can be the same when inserting to avoid unnecessary memory usage
        val payload = Array.fill(payloadSize)('a'.toByte)

        val lastInsert =
          Source
            .fromIterator(() => (1 to numberOfInsertBatches).iterator)
            .mapAsync(1) { i =>
              val end = i * eventsPerBatch
              val start = end - (eventsPerBatch - 1)
              log.info(s"batch $i - events from $start to $end")
              val atomicWrites =
                (start to end).map { j =>
                  AtomicWrite(immutable.Seq(PersistentRepr(payload, j, persistenceId, writerUuid = writerUuid)))
                }

              dao.asyncWriteMessages(atomicWrites).map(_ => i)
            }
            .runWith(Sink.last)

        // wait until we write all messages
        // being very generous, 1 second per message
        lastInsert.futureValue(Timeout(totalMessages.seconds))

        log.info("Events written, starting replay")

        // sleep and gc to have some kind of stable measurement of current heap usage
        Thread.sleep(1000)
        System.gc()
        Thread.sleep(1000)
        val usedBefore = memoryMBean.getHeapMemoryUsage.getUsed

        val messagesSrc =
          dao.messagesWithBatch(persistenceId, 0, totalMessages, batchSize = 100, None)
        val probe =
          messagesSrc
            .map {
              case Success((repr, _)) =>
                if (repr.sequenceNr % 100 == 0)
                  log.info(s"fetched: ${repr.persistenceId} - ${repr.sequenceNr}/${totalMessages}")
              case Failure(exception) =>
                log.error("Failure when reading messages.", exception)
            }
            .runWith(TestSink())

        probe.request(10)
        probe.within(20.seconds) {
          probe.expectNextN(10)
        }

        // sleep and gc to have some kind of stable measurement of current heap usage
        Thread.sleep(2000)
        System.gc()
        Thread.sleep(1000)
        val usedAfter = memoryMBean.getHeapMemoryUsage.getUsed

        log.info(s"Used heap before ${usedBefore / MB} MB, after ${usedAfter / MB} MB")
        // actual usage is much less than 10 MB
        (usedAfter - usedBefore) should be <= (10L * MB)

        probe.cancel()
      }
    }
  }
}
