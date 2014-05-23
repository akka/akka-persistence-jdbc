package akka.persistence.jdbc.journal

import akka.persistence.journal.SyncWriteJournal
import akka.persistence._
import scala.concurrent.Future
import scala.collection.immutable.Seq
import akka.persistence.jdbc.common.{ActorConfig, JdbcConnection}
import akka.serialization.SerializationExtension
import akka.persistence.jdbc.util.Base64
import akka.actor.ActorLogging
import RowTypeMarkers._

class JdbcSyncWriteJournal extends SyncWriteJournal with ActorLogging with ActorConfig with JdbcConnection {

  val extension = Persistence(context.system)
  val serialization = SerializationExtension(context.system)
  implicit val executionContext = context.system.dispatcher

  override def writeMessages(messages: Seq[PersistentRepr]): Unit = {
    log.debug(s"writeMessages for ${messages.size} presistent messages")

    withStatement { statement =>
      messages.foreach { message =>
        import message._
        val query = s"INSERT INTO event_store (processor_id, sequence_number, marker, message, created) VALUES ('$processorId', $sequenceNr, '$AcceptedMarker', '${Base64.encodeString(persistentToBytes(message))}', current_timestamp)"
        log.debug(query)
        statement.executeUpdate(query)
      }
    } match {
      case Left(msg) => log.error(msg.toString())
      case _ =>
    }
  }

  override def writeConfirmations(confirmations: Seq[PersistentConfirmation]): Unit = {
    log.debug(s"writeConfirmations for ${confirmations.size} messages")

    withStatement { statement =>
      confirmations.foreach { confirmation =>
        import confirmation._
        val query = s"UPDATE event_store SET marker = '${confirmedMarker(channelId)}' WHERE processor_id = '$processorId' and sequence_number = $sequenceNr"
        log.debug(query)
        statement.executeUpdate(query)
      }
    } match {
      case Left(msg) => log.error(msg.toString())
      case _ =>
    }
  }

  override def deleteMessagesTo(processorId: String, toSequenceNr: Long, permanent: Boolean): Unit = {
    log.debug(s"deleteMessagesTo for processorId: $processorId to sequenceNr: $toSequenceNr, premanent: $permanent")

    withStatement { statement =>
      val query = if(permanent) s"DELETE FROM event_store WHERE sequence_number <= $toSequenceNr and processor_id = '$processorId'"
                  else s"UPDATE event_store SET marker = '$DeletedMarker' WHERE sequence_number < $toSequenceNr and processor_id = '$processorId'"
      log.debug(query)
      statement.executeQuery(query)
    } match {
      case Left(msg) => log.error(msg.toString())
      case _ =>
    }
  }

  override def deleteMessages(messageIds: Seq[PersistentId], permanent: Boolean): Unit = {
    log.debug(s"Async delete [${messageIds.size}] messages, premanent: $permanent")

    withStatement { statement =>
      messageIds.foreach { persistentId =>
        import persistentId._
        val query = if(permanent) s"DELETE FROM event_store WHERE sequence_number = $sequenceNr and processor_id = '$processorId'"
                    else s"UPDATE event_store SET marker = '$DeletedMarker' WHERE sequence_number = $sequenceNr and processor_id = '$processorId'"
        log.debug(query)
        statement.executeQuery(query)
      }
    } match {
      case Left(msg) => log.error(msg.toString())
      case _ =>
    }
  }

  override def asyncReadHighestSequenceNr(processorId: String, fromSequenceNr: Long): Future[Long] = {
    log.debug(s"Async read for highest sequence number for processorId: [$processorId] (hint, seek from  nr: [$fromSequenceNr])")

    val query = s"SELECT MAX(sequence_number) FROM event_store WHERE processor_id = '$processorId'"
    log.debug(query)
    withResultSet[Long](query) { rs =>
       if (rs.next()) rs.getLong(1) else 0
    } match {
      case Left(errors) =>
        log.error(errors.toString())
        Future(-1)
      case Right(highestSequenceNr) => Future(highestSequenceNr)
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
      val query = s"SELECT message FROM event_store WHERE processor_id = '$pid' and (sequence_number >= $fsnr and sequence_number <= $tsnr) and marker != 'D' ORDER BY sequence_number limit $mx"

      withResultSet[Unit](query) { rs =>
        while(rs.next()) {
          replay(persistentFromBytes(Base64.decodeBinary(rs.getString(1))))
        }
      } match {
        case Left(errors) => log.error(errors.toString())
        case _ =>
      }
    }
  }

  def persistentToBytes(msg: Persistent): Array[Byte] = serialization.serialize(msg).get

  def persistentFromBytes(bytes: Array[Byte]): PersistentRepr = serialization.deserialize(bytes, classOf[PersistentRepr]).get

}
