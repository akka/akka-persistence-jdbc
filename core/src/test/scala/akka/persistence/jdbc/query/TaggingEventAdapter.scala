/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.query

import akka.persistence.jdbc.query.TaggingEventAdapter.TagEvent
import akka.persistence.journal.{ Tagged, WriteEventAdapter }

object TaggingEventAdapter {
  case class TagEvent(payload: Any, tags: Set[String])
}

/**
 * The TaggingEventAdapter will instruct persistence
 * to tag the received event.
 */
class TaggingEventAdapter extends WriteEventAdapter {
  override def manifest(event: Any): String = ""

  override def toJournal(event: Any): Any =
    event match {
      case TagEvent(payload, tags) =>
        Tagged(payload, tags)
      case _ => event
    }
}
