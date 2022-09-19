/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc

final case class JournalRow(
    ordering: Long,
    deleted: Boolean,
    persistenceId: String,
    sequenceNumber: Long,
    message: Array[Byte],
    tags: Option[String] = None)
