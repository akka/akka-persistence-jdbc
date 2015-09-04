/*
 * Copyright 2015 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.persistence.jdbc.journal

import akka.actor.ActorLogging
import akka.persistence._
import akka.persistence.jdbc.common.ActorConfig
import akka.persistence.jdbc.future.PimpedFuture
import akka.persistence.jdbc.journal.RowTypeMarkers._
import akka.persistence.journal.SyncWriteJournal

import scala.collection.immutable.Seq
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

trait JdbcSyncWriteJournal extends SyncWriteJournal with ActorLogging with ActorConfig with JdbcStatements {

  implicit def executionContext: ExecutionContext

  override def writeMessages(messages: Seq[PersistentRepr]): Unit = insertMessages(messages)

  override def writeConfirmations(confirmations: Seq[PersistentConfirmation]): Unit = {
    log.debug(s"writeConfirmations for ${confirmations.size} messages")

    confirmations.foreach { confirmation ⇒
      import confirmation._
      selectMessage(persistenceId, sequenceNr).toStream.foreach { msg ⇒
        val confirmationIds = msg.confirms :+ confirmation.channelId
        val marker = confirmedMarker(confirmationIds)
        val updatedMessage = msg.update(confirms = confirmationIds)
        updateMessage(persistenceId, sequenceNr, marker, updatedMessage)
      }
    }
  }

  override def deleteMessagesTo(processorId: String, toSequenceNr: Long, permanent: Boolean): Unit =
    if (permanent) {
      deleteMessageRange(processorId, toSequenceNr)
    } else {
      (1 to toSequenceNr.toInt).toList.map(_.toLong).foreach { sequenceNr ⇒
        selectMessage(processorId, sequenceNr).foreach { msg ⇒
          updateMessage(processorId, sequenceNr, DeletedMarker, msg.update(deleted = true))
        }
      }
    }

  override def deleteMessages(messageIds: Seq[PersistentId], permanent: Boolean): Unit =
    messageIds.foreach { persistentId ⇒
      import persistentId._
      if (permanent) {
        deleteMessageSingle(processorId, sequenceNr)
      } else {
        selectMessage(processorId, sequenceNr).foreach { msg ⇒
          updateMessage(processorId, sequenceNr, DeletedMarker, msg.update(deleted = true))
        }
      }
    }

  override def asyncReadHighestSequenceNr(processorId: String, fromSequenceNr: Long): Future[Long] =
    PimpedFuture.fromTry(Try(selectMaxSequenceNr(processorId)))

  override def asyncReplayMessages(processorId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) ⇒ Unit): Future[Unit] =
    PimpedFuture.fromTry(Try(selectMessagesFor(processorId, fromSequenceNr, toSequenceNr, max).foreach(replayCallback)))
}
