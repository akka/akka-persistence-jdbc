/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.util

import java.io.{ ByteArrayOutputStream, InputStream }

import scala.concurrent.blocking

object InputStreamOps {
  implicit class InputStreamImplicits(val is: InputStream) extends AnyVal {
    def toArray: Array[Byte] =
      blocking {
        /* based on https://stackoverflow.com/a/17861016/865265
         * Thanks to
         * - https://stackoverflow.com/users/1435969/ivan-gammel
         * - https://stackoverflow.com/users/2619133/oliverkn
         */
        val bos: ByteArrayOutputStream = new ByteArrayOutputStream
        val buffer: Array[Byte] = new Array[Byte](0xffff)
        var len: Int = is.read(buffer)
        while (len != -1) {
          bos.write(buffer, 0, len)
          len = is.read(buffer)
        }
        bos.toByteArray
      }
  }
}
