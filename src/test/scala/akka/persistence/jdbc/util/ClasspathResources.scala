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

import java.io.InputStream

import akka.NotUsed
import akka.stream.IOResult
import akka.stream.scaladsl.{ Source, StreamConverters }
import akka.util.ByteString

import scala.concurrent.Future
import scala.io.{ Source => ScalaIOSource }
import scala.util.Try
import scala.xml.pull.{ XMLEvent, XMLEventReader }

object ClasspathResources extends ClasspathResources

trait ClasspathResources {
  def withInputStream[T](fileName: String)(f: InputStream => T): T = {
    val is = fromClasspathAsStream(fileName)
    try {
      f(is)
    } finally {
      Try(is.close())
    }
  }

  def withXMLEventReader[T](fileName: String)(f: XMLEventReader => T): T =
    withInputStream(fileName) { is =>
      f(new XMLEventReader(ScalaIOSource.fromInputStream(is)))
    }

  def withXMLEventSource[T](fileName: String)(f: Source[XMLEvent, NotUsed] => T): T =
    withXMLEventReader(fileName) { reader =>
      f(Source.fromIterator(() => reader))
    }

  def withByteStringSource[T](fileName: String)(f: Source[ByteString, Future[IOResult]] => T): T =
    withInputStream(fileName) { inputStream =>
      f(StreamConverters.fromInputStream(() => inputStream))
    }

  def streamToString(is: InputStream): String =
    ScalaIOSource.fromInputStream(is).mkString

  def fromClasspathAsString(fileName: String): String =
    streamToString(fromClasspathAsStream(fileName))

  def fromClasspathAsStream(fileName: String): InputStream =
    getClass.getClassLoader.getResourceAsStream(fileName)

}
