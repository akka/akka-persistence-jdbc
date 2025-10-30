/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2025 Lightbend Inc. <https://akka.io>
 */

package akka.persistence.jdbc.journal.dao

private[jdbc] sealed trait FlowControl

private[jdbc] object FlowControl {

  /** Keep querying - used when we are sure that there is more events to fetch */
  case object Continue extends FlowControl

  /**
   * Keep querying with delay - used when we have consumed all events,
   * but want to poll for future events
   */
  case object ContinueDelayed extends FlowControl

  /** Stop querying - used when we reach the desired offset */
  case object Stop extends FlowControl
}
