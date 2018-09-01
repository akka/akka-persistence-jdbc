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

package akka.persistence

package object jdbc {
  final case class JournalRow(
      ordering: Long,
      deleted: Boolean,
      persistenceId: String,
      sequenceNumber: Long,
      @deprecated("This was used to store the the PersistentRepr serialized as is, but no longer.", "4.0.0") message: Option[Array[Byte]],
      tags: Option[String] = None,
      // These are all options because they were added in 4.0.0, events written earlier won't have them.
      event: Option[Array[Byte]],
      eventManifest: Option[String],
      serId: Option[Int],
      serManifest: Option[String],
      writerUuid: Option[String])
}
