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

package akka.persistence.jdbc.util

import java.io.{ ByteArrayOutputStream, InputStream }

import scala.concurrent.blocking

object InputStreamOps {
  implicit class InputStreamImplicits(val is: InputStream) extends AnyVal {
    def toArray: Array[Byte] = blocking {
      /* based on https://stackoverflow.com/a/17861016/865265
       * Thanks to
       * - https://stackoverflow.com/users/1435969/ivan-gammel
       * - https://stackoverflow.com/users/2619133/oliverkn
       */
      val bos: ByteArrayOutputStream = new ByteArrayOutputStream
      val buffer: Array[Byte] = new Array[Byte](0xFFFF)
      var len: Int = is.read(buffer)
      while (len != -1) {
        bos.write(buffer, 0, len)
        len = is.read(buffer)
      }
      bos.toByteArray
    }
  }
}
