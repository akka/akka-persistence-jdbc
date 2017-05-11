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

package akka.persistence.jdbc.query.adapter

import akka.persistence.journal.{EventSeq, ReadEventAdapter}

/**
  * Event adapter that creates two events from one persisted event when using [[DomainEvent]]
  * The domain event is doubled and the content is concatenated with itself as a result.
  */
class DomainEventAdapter extends ReadEventAdapter {
  override def fromJournal(event: Any, manifest: String): EventSeq = {
    event match {
      case s: DomainEvent => EventSeq(s, s.copy(value = s.value+s.value))
      case _ => EventSeq.single(event)
    }
  }
}