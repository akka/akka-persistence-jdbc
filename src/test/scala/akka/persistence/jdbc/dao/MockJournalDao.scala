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

package akka.persistence.jdbc.dao

import akka.persistence.jdbc.serialization.Serialized
import akka.stream.scaladsl.{ Flow, Source }

import scala.concurrent.Future
import scala.util.Try

class MockJournalDao(fail: Boolean = false) extends JournalDao {
  override def writeList(xs: Iterable[Serialized]): Future[Unit] =
    if (fail) Future.failed(new RuntimeException("Mock cannot write message list")) else Future.successful(())

  override def writeFlow: Flow[Try[Iterable[Serialized]], Try[Iterable[Serialized]], Unit] =
    Flow[Try[Iterable[Serialized]]].map { e ⇒ if (fail) throw new RuntimeException("Mock cannot write message flow") else e }

  override def highestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] =
    if (fail) Future.failed(new RuntimeException("Mock cannot request highest sequence number")) else Future.successful(0)

  override def delete(persistenceId: String, toSequenceNr: Long): Future[Unit] =
    if (fail) Future.failed(new RuntimeException("Mock cannot delete message to")) else Future.successful(())

  override def messages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long): Source[Array[Byte], Unit] =
    Source.single(Array.empty[Byte]).map(e ⇒ if (fail) throw new RuntimeException("Mock cannot read message") else e)

  override def allPersistenceIdsSource: Source[String, Unit] =
    Source.single("pid-1").map(e ⇒ if (fail) throw new RuntimeException("Mock cannot read message") else e)

  override def persistenceIds(queryListOfPersistenceIds: Iterable[String]): Future[Seq[String]] =
    if (fail) Future.failed(new RuntimeException("Mock cannot delete message to")) else Future.successful(Nil)

  override def eventsByTag(tag: String, tagPrefix: String, offset: Long): Source[Array[Byte], Unit] =
    Source.single(Array.empty[Byte]).map(e ⇒ if (fail) throw new RuntimeException("Mock cannot eventsByTag") else e)
}
