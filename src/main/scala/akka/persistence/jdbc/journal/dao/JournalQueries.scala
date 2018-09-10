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

package akka.persistence.jdbc
package journal.dao

import akka.NotUsed
import akka.persistence.jdbc.config.JournalTableConfiguration
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import slick.dbio.NoStream
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.JdbcProfile
import slick.sql.FixedSqlAction

import scala.collection.immutable.Seq
import scala.concurrent.{ ExecutionContext, Future }

class GenericJournalQueries(val profile: JdbcProfile, override val journalTableCfg: JournalTableConfiguration) extends JournalTables {
  import profile.api._

  private def selectAllJournalForPersistenceId(persistenceId: Rep[String]) =
    GenericJournalTable.filter(_.persistenceId === persistenceId).sortBy(_.sequenceNumber.desc)

  def markJournalMessagesAsDeleted(persistenceId: String, maxSequenceNr: Long) =
    GenericJournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNumber <= maxSequenceNr)
      .filter(_.deleted === false)
      .map(_.deleted)
      .update(true)

  private def _highestSequenceNrForPersistenceId(persistenceId: Rep[String]) =
    selectAllJournalForPersistenceId(persistenceId).map(_.sequenceNumber).take(1)

  private def _highestMarkedSequenceNrForPersistenceId(persistenceId: Rep[String]) =
    selectAllJournalForPersistenceId(persistenceId).filter(_.deleted === true).map(_.sequenceNumber)

  val highestSequenceNrForPersistenceId = Compiled(_highestSequenceNrForPersistenceId _)

  val highestMarkedSequenceNrForPersistenceId = Compiled(_highestMarkedSequenceNrForPersistenceId _)

  private def _allPersistenceIdsDistinct =
    GenericJournalTable.map(_.persistenceId).distinct

  val allPersistenceIdsDistinct = Compiled(_allPersistenceIdsDistinct)

  def journalRowByPersistenceIds(persistenceIds: Iterable[String]) = for {
    query <- GenericJournalTable.map(_.persistenceId)
    if query inSetBind persistenceIds
  } yield query

}

trait JournalQueries[T <: AbstractJournalRow] {
  import slick.dbio.Effect
  implicit val ec: ExecutionContext
  implicit val mat: Materializer

  def writeJournalRows(xs: Seq[T]): Future[Unit]
  def delete(persistenceId: String, toSequenceNr: Long): FixedSqlAction[Int, NoStream, Effect.Write]
  def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[T, NotUsed]
  def update(persistenceId: String, seqNr: Long, row: T): Future[Int]
  val db: Database
}

class LegacyJournalQueries(
    val db: Database,
    val profile: JdbcProfile,
    override val journalTableCfg: JournalTableConfiguration)(implicit val ec: ExecutionContext, val mat: Materializer)
  extends JournalTables with JournalQueries[LegacyJournalRow] {
  import profile.api._
  import slick.dbio.Effect

  def writeJournalRows(xs: Seq[LegacyJournalRow]): Future[Unit] = {
    for {
      _ <- db.run(LegacyJournalTable ++= xs.sortBy(_.sequenceNumber))
    } yield ()
  }

  def update(persistenceId: String, seqNr: Long, row: LegacyJournalRow): Future[Int] = {
    val q = {
      val baseQuery = LegacyJournalTable
        .filter(_.persistenceId === persistenceId)
        .filter(_.sequenceNumber === seqNr)

      baseQuery.map(row => (row.message, row.event, row.serId, row.serManifest)).update((row.message, row.event, row.serId, row.serManifest))
    }
    db.run(q)
  }

  def delete(persistenceId: String, toSequenceNr: Long): FixedSqlAction[Int, NoStream, Effect.Write] = {
    LegacyJournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNumber <= toSequenceNr)
      .delete
  }

  override def messages(
    persistenceId: String,
    fromSequenceNr: Long,
    toSequenceNr: Long,
    max: Long): Source[LegacyJournalRow, NotUsed] = Source.fromPublisher(db.stream(messagesQuery(persistenceId, fromSequenceNr, toSequenceNr, max).result))

  private def updateJournalTable(persistenceId: String, seqNr: Long, replacementMessage: Option[Array[Byte]], replacementEvent: Option[Array[Byte]], replacementSerId: Option[Int], replacementSerManifest: Option[String]): FixedSqlAction[Int, NoStream, Effect.Write] = {
    val baseQuery = LegacyJournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNumber === seqNr)

    baseQuery.map(row => (row.message, row.event, row.serId, row.serManifest)).update((replacementMessage, replacementEvent, replacementSerId, replacementSerManifest))
  }

  private def _messagesQuery(persistenceId: Rep[String], fromSequenceNr: Rep[Long], toSequenceNr: Rep[Long], max: ConstColumn[Long]) =
    LegacyJournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.deleted === false)
      .filter(_.sequenceNumber >= fromSequenceNr)
      .filter(_.sequenceNumber <= toSequenceNr)
      .sortBy(_.sequenceNumber.asc)
      .take(max)

  private val messagesQuery = Compiled(_messagesQuery _)

}

class NewJournalQueries(
    val db: Database,
    val profile: JdbcProfile,
    override val journalTableCfg: JournalTableConfiguration)(implicit val ec: ExecutionContext, val mat: Materializer)
  extends JournalTables with JournalQueries[JournalRow] {

  import profile.api._

  private val JournalTableC = Compiled(JournalTable)

  def writeJournalRows(xs: Seq[JournalRow]): Future[Unit] = {
    for {
      _ <- db.run(JournalTable ++= xs.sortBy(_.sequenceNumber))
    } yield ()
  }

  def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[JournalRow, NotUsed] = {
    Source.fromPublisher(db.stream(messagesQuery(persistenceId, fromSequenceNr, toSequenceNr, max).result))
  }

  def update(persistenceId: String, seqNr: Long, row: JournalRow): Future[Int] = {
    val q = {
      val baseQuery = JournalTable
        .filter(_.persistenceId === persistenceId)
        .filter(_.sequenceNumber === seqNr)

      baseQuery.map(row => (row.event, row.serId, row.serManifest)).update((row.event, row.serId, row.serManifest))
    }
    db.run(q)
  }

  def delete(persistenceId: String, toSequenceNr: Long): FixedSqlAction[Int, NoStream, Effect.Write] = {
    JournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNumber <= toSequenceNr)
      .delete
  }

  private def updateJournalTable(persistenceId: String, seqNr: Long, replacementEvent: Array[Byte], replacementSerId: Int, replacementSerManifest: Option[String]) = {
    val baseQuery = JournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNumber === seqNr)

    baseQuery.map(row => (row.event, row.serId, row.serManifest)).update((replacementEvent, replacementSerId, replacementSerManifest))
  }

  private def _messagesQuery(persistenceId: Rep[String], fromSequenceNr: Rep[Long], toSequenceNr: Rep[Long], max: ConstColumn[Long]) =
    JournalTable
      .filter(_.persistenceId === persistenceId)
      .filter(_.deleted === false)
      .filter(_.sequenceNumber >= fromSequenceNr)
      .filter(_.sequenceNumber <= toSequenceNr)
      .sortBy(_.sequenceNumber.asc)
      .take(max)

  private val messagesQuery = Compiled(_messagesQuery _)

}
