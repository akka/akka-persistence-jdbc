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

  def selectMessage(processorId: String, sequenceNr: Long): Option[PersistentRepr] =
    sql"SELECT message FROM event_store WHERE processor_id = ${processorId} AND sequence_number = ${sequenceNr}"
      .map(rs => fromBytes(Base64.decodeBinary(rs.string(1))))
      .single()
      .apply()

  def insertMessage(processorId: String, sequenceNr: Long, marker: String = "A", message: Persistent) {
    val msgToWrite = Base64.encodeString(toBytes(message))
    sql"""INSERT INTO event_store (processor_id, sequence_number, marker, message, created) VALUES
            (${processorId},
            ${sequenceNr},
            ${marker},
            ${msgToWrite},
            current_timestamp)""".update.apply
  }

  def updateMessage(processorId: String, sequenceNr: Long, marker: String, message: Persistent) {
    log.debug("Updating message for processorId: {}, sequenceNr: {}, marker: {} ", processorId, sequenceNr, marker)
    val msgToWrite = Base64.encodeString(toBytes(message))
    sql"UPDATE event_store SET message = ${msgToWrite}, marker = ${marker} WHERE processor_id = ${processorId} and sequence_number = ${sequenceNr}".update.apply
  }

  override def writeMessages(messages: Seq[PersistentRepr]): Unit = {
    log.debug(s"writeMessages for ${messages.size} presistent messages")

    messages.foreach { message =>
      import message._
      insertMessage(processorId, sequenceNr, AcceptedMarker, message)
    }
  }

  override def writeConfirmations(confirmations: Seq[PersistentConfirmation]): Unit = {
    log.debug(s"writeConfirmations for ${confirmations.size} messages")

    confirmations.foreach { confirmation =>
      import confirmation._
      selectMessage(processorId, sequenceNr) match {
        case Some(msg) =>
          val confirmationIds = msg.confirms :+ confirmation.channelId
          val marker = confirmedMarker(confirmationIds)
          val updatedMessage = msg.update(confirms = confirmationIds)
          updateMessage(processorId, sequenceNr, marker, updatedMessage)
        case _ => log.error("Could not write configuration message for confirmations: {}", confirmations)
      }
    }
  }

  override def deleteMessagesTo(processorId: String, toSequenceNr: Long, permanent: Boolean): Unit = {
    log.debug(s"deleteMessagesTo for processorId: $processorId to sequenceNr: $toSequenceNr, permanent: $permanent")

     if(permanent)
       sql"DELETE FROM event_store WHERE sequence_number <= ${toSequenceNr} and processor_id = ${processorId}".update.apply
     else
       (1 to toSequenceNr.toInt).toList.map(_.toLong).foreach { sequenceNr =>
         selectMessage(processorId, sequenceNr) match {
           case Some(msg) =>
             val deletedMsg = msg.update(deleted = true)
             updateMessage(processorId, sequenceNr, DeletedMarker, deletedMsg)
           case _ => log.error("Could not delete messages with processorId: {} and sequenceNr: {}" , processorId, sequenceNr)
         }
       }
  }

  override def deleteMessages(messageIds: Seq[PersistentId], permanent: Boolean): Unit = {
    log.debug(s"Async delete [${messageIds.size}] messages, premanent: $permanent")

    messageIds.foreach { persistentId =>
      import persistentId._
      if (permanent)
        sql"DELETE FROM event_store WHERE sequence_number = ${sequenceNr} and processor_id = ${processorId}".update.apply
      else
        selectMessage(processorId, sequenceNr) match {
          case Some(msg) =>
          val deletedMsg = msg.update(deleted = true)
            updateMessage(processorId, sequenceNr, DeletedMarker, deletedMsg)
          case _ => log.error("Could not delete messages with ids: {}", messageIds)
        }
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
    log.debug(s"Async replay for processorId [$processorId], from sequenceNr: [$fromSequenceNr], to sequenceNr: [$toSequenceNr] with max records: [$max]")

    Future[Unit] {
      val pid = processorId
      val fsnr = fromSequenceNr
      val tsnr = toSequenceNr
      val mx = max
      val replay = replayCallback
      val result = sql"SELECT message FROM event_store WHERE processor_id = ${pid} and (sequence_number >= ${fsnr} and sequence_number <= ${tsnr}) ORDER BY sequence_number limit ${mx}"
        .map(rs => fromBytes(Base64.decodeBinary(rs.string(1))))
        .list()
        .apply
        .foreach(replay)
    }
  }

  def toBytes(msg: Persistent): Array[Byte] = serialization.serialize(msg).get

  def fromBytes(bytes: Array[Byte]): PersistentRepr = serialization.deserialize(bytes, classOf[PersistentRepr]).get
}