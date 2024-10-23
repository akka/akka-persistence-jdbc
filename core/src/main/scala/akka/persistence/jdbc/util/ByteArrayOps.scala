/*
 * Copyright (C) 2014 - 2019 Dennis Vriend <https://github.com/dnvriend>
 * Copyright (C) 2019 - 2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.jdbc.util

import java.io.{ ByteArrayInputStream, InputStream }
import java.util.Base64

object ByteArrayOps {
  implicit class ByteArrayImplicits(val that: Array[Byte]) extends AnyVal {
    def encodeBase64: String = Base64.getEncoder.encodeToString(that)
    def toInputStream: InputStream = new ByteArrayInputStream(that)
  }
}
