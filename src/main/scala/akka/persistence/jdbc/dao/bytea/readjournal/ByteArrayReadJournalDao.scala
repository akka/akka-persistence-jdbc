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

package akka.persistence.jdbc.dao.bytea.readjournal

import akka.NotUsed
import akka.persistence.PersistentRepr
import akka.persistence.jdbc.config.ReadJournalConfig
import akka.persistence.jdbc.dao.ReadJournalDao
import akka.persistence.jdbc.dao.bytea.readjournal.ReadJournalTables.JournalRow
import akka.serialization.Serialization
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import slick.driver.JdbcProfile
import slick.jdbc.GetResult
import slick.jdbc.JdbcBackend._

import scala.concurrent.ExecutionContext
import scala.util.Try

class ByteArrayReadJournalDao(db: Database, val profile: JdbcProfile, readJournalConfig: ReadJournalConfig, serialization: Serialization)(implicit ec: ExecutionContext, mat: Materializer) extends ReadJournalDao {
  import profile.api._
  val queries = new ReadJournalQueries(profile, readJournalConfig.journalTableConfiguration)

  val serializer = new ByteArrayReadJournalSerializer(serialization, readJournalConfig.pluginConfig.tagSeparator)

  private def oracleAllPersistenceIds(max: Long): Source[String, NotUsed] = {
    import readJournalConfig.journalTableConfiguration._
    import columnNames._
    Source.fromPublisher(
      db.stream(sql"""select distinct "#$persistenceId" from "#${schemaName.getOrElse("")}"."#$tableName" where rownum <= $max""".as[String])
    )
  }

  private def defaultAllPersistenceIds(max: Long): Source[String, NotUsed] =
    Source.fromPublisher(db.stream(queries.allPersistenceIdsDistinct(max).result))

  override def allPersistenceIdsSource(max: Long): Source[String, NotUsed] = profile match {
    case com.typesafe.slick.driver.oracle.OracleDriver ⇒ oracleAllPersistenceIds(max)
    case _                                             ⇒ defaultAllPersistenceIds(correctMaxForDBDriver(max))
  }

  implicit val getJournalRow = GetResult(r ⇒ JournalRow(r.<<, r.<<, r.nextBytes(), r.<<, r.<<))

  private def oracleEventsByTag(tag: String, offset: Long, max: Long): Source[JournalRow, NotUsed] = {
    import readJournalConfig.journalTableConfiguration._
    import columnNames._
    val theOffset = Math.max(1, offset) - 1
    val theTag = s"%$tag%"
    Source.fromPublisher(
      db.stream(
        sql"""SELECT "#$persistenceId", "#$sequenceNumber", "#$message", "#$created", "#$tags" FROM (
              SELECT
                a.*,
                rownum rnum
              FROM
                (SELECT *
                 FROM "#${schemaName.getOrElse("")}"."#$tableName"
                 WHERE "#$tags" LIKE $theTag
                 ORDER BY "#$created") a
              where rownum <= $max
            )
            where rnum > $theOffset""".as[JournalRow]
      )
    )
  }

  private def defaultEventsByTag(tag: String, offset: Long, max: Long): Source[JournalRow, NotUsed] =
    Source.fromPublisher(db.stream(queries.eventsByTag(s"%$tag%", Math.max(1, offset) - 1, correctMaxForDBDriver(max)).result))

  override def eventsByTag(tag: String, offset: Long, max: Long): Source[Try[PersistentRepr], NotUsed] = {
    val source: Source[JournalRow, NotUsed] = profile match {
      case com.typesafe.slick.driver.oracle.OracleDriver ⇒ oracleEventsByTag(tag, offset, max)
      case _                                             ⇒ defaultEventsByTag(tag, offset, max)
    }
    source.via(serializer.deserializeFlowWithoutTags)
  }

  override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[Try[PersistentRepr], NotUsed] =
    Source.fromPublisher(db.stream(queries.messagesQuery(persistenceId, fromSequenceNr, toSequenceNr,
                                                         correctMaxForDBDriver(max)).result))
      .via(serializer.deserializeFlowWithoutTags)

  private def correctMaxForDBDriver(max: Long): Long = {
    profile match {
      case slick.driver.H2Driver ⇒ Math.min(max, Int.MaxValue) // H2 only accepts a LIMIT clause as an Integer
      case _                     ⇒ max
    }
  }
}
