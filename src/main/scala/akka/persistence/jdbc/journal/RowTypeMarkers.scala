package akka.persistence.jdbc.journal

object RowTypeMarkers {
  val AcceptedMarker = "A"
  val DeletedMarker = "D"
  val SnapshotMarker = "S"
  def confirmedMarker(channelId: String) = s"C-$channelId"
  def extractSeqNrFromConfirmedMarker(marker: String): String = marker.substring(2)
}
