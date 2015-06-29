/*
 * Copyright 2015 Dennis Vriend
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

package akka.persistence.jdbc.serialization.snapshot

import akka.persistence.jdbc.serialization.SnapshotTypeConverter
import akka.persistence.serialization.Snapshot
import akka.serialization.Serialization
import org.apache.commons.codec.binary.Base64

class Base64SnapshotConverter extends SnapshotTypeConverter {
  override def marshal(value: Snapshot)(implicit serialization: Serialization): String =
    serialization.serialize(value).map(new Base64().encodeToString).get

  override def unmarshal(value: String, persistenceId: String)(implicit serialization: Serialization): Snapshot =
    serialization.deserialize(new Base64().decode(value), classOf[Snapshot]).get
}
