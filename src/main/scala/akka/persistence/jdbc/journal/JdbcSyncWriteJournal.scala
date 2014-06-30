package akka.persistence.jdbc.journal

import akka.persistence.journal.SyncWriteJournal
import akka.persistence._
import scala.concurrent.Future
import scala.collection.immutable.Seq
import akka.persistence.jdbc.common.{ScalikeConnection, ActorConfig}
import akka.serialization.SerializationExtension
import akka.persistence.jdbc.util.Base64
import akka.actor.ActorLogging
import RowTypeMarkers._
import scalikejdbc._

class JdbcSyncWriteJournal extends SyncWriteJournal with ActorLogging with ActorConfig with ScalikeConnection {
  implicit val system = context.system
  val extension = Persistence(context.system)
  val serialization = SerializationExtension(context.system)
  implicit val executionContext = context.system.dispatcher

  override def writeMessages(messages: Seq[PersistentRepr]): Unit = {
    log.debug(s"writeMessages for ${messages.size} presistent messages")

    messages.foreach { message =>
      import message._
      val msgToWrite = Base64.encodeString(persistentToBytes(message))
      sql"""INSERT INTO event_store (processor_id, sequence_number, marker, message, created) VALUES
            (${processorId},
            ${sequenceNr},
            ${AcceptedMarker},
            ${msgToWrite},
            current_timestamp)""".update.apply
    }
  }

  override def writeConfirmations(confirmations: Seq[PersistentConfirmation]): Unit = {
    log.debug(s"writeConfirmations for ${confirmations.size} messages")

    confirmations.foreach { confirmation =>
      import confirmation._
      val marker = confirmedMarker(channelId)
      sql"UPDATE event_store SET marker = ${marker} WHERE processor_id = ${processorId} and sequence_number = ${sequenceNr}".update.apply
    }
  }

  override def deleteMessagesTo(processorId: String, toSequenceNr: Long, permanent: Boolean): Unit = {
    log.debug(s"deleteMessagesTo for processorId: $processorId to sequenceNr: $toSequenceNr, permanent: $permanent")

     if(permanent)
       sql"DELETE FROM event_store WHERE sequence_number <= ${toSequenceNr} and processor_id = ${processorId}".update.apply
     else
       sql"UPDATE event_store SET marker = ${DeletedMarker} WHERE sequence_number < ${toSequenceNr} and processor_id = ${processorId}".update.apply
  }

  override def deleteMessages(messageIds: Seq[PersistentId], permanent: Boolean): Unit = {
    log.debug(s"Async delete [${messageIds.size}] messages, premanent: $permanent")

    messageIds.foreach { persistentId =>
      import persistentId._
      if (permanent)
        sql"DELETE FROM event_store WHERE sequence_number = ${sequenceNr} and processor_id = ${processorId}".update.apply
      else
        sql"UPDATE event_store SET marker = ${DeletedMarker} WHERE sequence_number = ${sequenceNr} and processor_id = ${processorId}".update.apply
    }
  }

  override def asyncReadHighestSequenceNr(processorId: String, fromSequenceNr: Long): Future[Long] = {
    log.debug(s"Async read for highest sequence number for processorId: [$processorId] (hint, seek from  nr: [$fromSequenceNr])")

    Future[Long] {
      val pid = processorId
      sql"SELECT MAX(sequence_number) FROM event_store WHERE processor_id = ${pid}"
        .map(_.longOpt(1))
        .single()
        .apply()
        .flatMap(identity)
        .getOrElse(0)
    }
  }

  override def asyncReplayMessages(processorId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {
    log.debug(s"Async replay for processorId [$processorId], from sequenceNr: [$fromSequenceNr], to sequenceNr: [$toSequenceNr]")

    Future[Unit] {
      val pid = processorId
      val fsnr = fromSequenceNr
      val tsnr = toSequenceNr
      val mx = max
      val replay = replayCallback
      sql"SELECT message FROM event_store WHERE processor_id = ${pid} and (sequence_number >= ${fsnr} and sequence_number <= ${tsnr}) and marker != 'D' ORDER BY sequence_number limit ${mx}"
        .map(_.string(1))
        .list()
        .apply
        .foreach { msg =>
          replay(persistentFromBytes(Base64.decodeBinary(msg)))
        }
    }
  }

  def persistentToBytes(msg: Persistent): Array[Byte] = serialization.serialize(msg).get

  def persistentFromBytes(bytes: Array[Byte]): PersistentRepr = serialization.deserialize(bytes, classOf[PersistentRepr]).get
}