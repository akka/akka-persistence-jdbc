package akka.persistence.jdbc.journal

import akka.actor.ActorLogging
import akka.persistence._
import akka.persistence.jdbc.common.ActorConfig
import akka.persistence.jdbc.journal.RowTypeMarkers._
import akka.persistence.journal.SyncWriteJournal
import akka.serialization.SerializationExtension

import scala.collection.immutable.Seq
import scala.concurrent.Future

trait JdbcSyncWriteJournal extends SyncWriteJournal with ActorLogging with ActorConfig with JdbcStatements {
  implicit val system = context.system
  val extension = Persistence(context.system)
  val serialization = SerializationExtension(context.system)
  implicit val executionContext = context.system.dispatcher

  override def writeMessages(messages: Seq[PersistentRepr]): Unit = {
    log.debug(s"writeMessages for ${messages.size} persistent messages")

    messages.foreach { message =>
      import message._
      insertMessage(processorId, sequenceNr, AcceptedMarker, message)
    }
  }

  override def writeConfirmations(confirmations: Seq[PersistentConfirmation]): Unit = {
    log.debug(s"writeConfirmations for ${confirmations.size} messages")

    confirmations.foreach { confirmation =>
      import confirmation._
      selectMessage(persistenceId, sequenceNr).map { msg =>
        val confirmationIds = msg.confirms :+ confirmation.channelId
        val marker = confirmedMarker(confirmationIds)
        val updatedMessage = msg.update(confirms = confirmationIds)
        updateMessage(persistenceId, sequenceNr, marker, updatedMessage)
      }
    }
  }

  override def deleteMessagesTo(processorId: String, toSequenceNr: Long, permanent: Boolean): Unit = {
    log.debug(s"deleteMessagesTo for processorId: $processorId to sequenceNr: $toSequenceNr, permanent: $permanent")

     permanent match {
       case true => deleteMessageRange(processorId, toSequenceNr)
       case false => (1 to toSequenceNr.toInt).toList.map(_.toLong).foreach { sequenceNr =>
         selectMessage(processorId, sequenceNr).map { msg =>
           updateMessage(processorId, sequenceNr, DeletedMarker, msg.update(deleted = true))
         }
       }
     }
  }

  override def deleteMessages(messageIds: Seq[PersistentId], permanent: Boolean): Unit = {
    log.debug(s"Async delete [${messageIds.size}] messages, premanent: $permanent")

    messageIds.foreach { persistentId =>
      import persistentId._
      permanent match {
        case true => deleteMessageSingle(processorId, sequenceNr)
        case false => selectMessage(processorId, sequenceNr).map { msg =>
          updateMessage(processorId, sequenceNr, DeletedMarker, msg.update(deleted = true))
        }
      }
    }
  }

  override def asyncReadHighestSequenceNr(processorId: String, fromSequenceNr: Long): Future[Long] = {
    log.debug(s"Async read for highest sequence number for processorId: [$processorId] (hint, seek from  nr: [$fromSequenceNr])")
    selectMaxSequenceNr(processorId)
  }

  override def asyncReplayMessages(processorId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {
    log.debug(s"Async replay for processorId [$processorId], from sequenceNr: [$fromSequenceNr], to sequenceNr: [$toSequenceNr] with max records: [$max]")

    Future[Unit] {
      selectMessagesFor(processorId, fromSequenceNr, toSequenceNr, max).foreach(replayCallback)
    }
  }
}
