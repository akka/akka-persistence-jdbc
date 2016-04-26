/*
 * Copyright 2016 Dennis Vriend
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

package akka.persistence.jdbc.query.journal.publisher

import akka.actor.{ ActorLogging, ActorRef }
import akka.persistence.Persistence
import akka.persistence.jdbc.journal.JdbcJournal
import akka.persistence.jdbc.query.journal.scaladsl.JdbcReadJournal
import akka.persistence.query.EventEnvelope
import akka.persistence.query.journal.leveldb.DeliveryBuffer
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }

class EventsByPersistenceIdAndTagPublisher(persistenceId: String, tag: String)
    extends ActorPublisher[EventEnvelope] with DeliveryBuffer[EventEnvelope] with ActorLogging {

  val journal: ActorRef = Persistence(context.system).journalFor(JdbcJournal.Identifier)

  def receive = init

  def init: Receive = {
    case _: Request ⇒
      journal ! JdbcReadJournal.EventsByPersistenceIdAndTagRequest(persistenceId, tag)
      context.become(active)
    case Cancel ⇒ context.stop(self)
  }

  def active: Receive = {
    case JdbcReadJournal.EventAppended(envelope) ⇒
      buf :+= envelope
      deliverBuf()

    case _: Request ⇒
      deliverBuf()

    case Cancel ⇒ context.stop(self)
  }
}
