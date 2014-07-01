package akka.persistence.jdbc.journal

object RowTypeMarkers {
  val AcceptedMarker = "A"
  val DeletedMarker = "D"
  val SnapshotMarker = "S"
  def confirmedMarker(channelIds: Seq[String]) = channelIds.foldLeft[String]("C-") { (c, id) => c + id }
  def extractSeqNrFromConfirmedMarker(marker: String): String = marker.substring(2)
}
