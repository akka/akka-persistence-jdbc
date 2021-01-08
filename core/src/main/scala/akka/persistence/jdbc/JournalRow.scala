package akka.persistence.jdbc

final case class JournalRow(
    ordering: Long,
    deleted: Boolean,
    persistenceId: String,
    sequenceNumber: Long,
    message: Array[Byte],
    tags: Option[String] = None)
