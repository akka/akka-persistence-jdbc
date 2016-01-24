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

import akka.persistence.jdbc.util.ClasspathResources
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.prop.PropertyChecks
import org.scalatest._

trait SimpleSpec extends FlatSpec
  with Matchers
  with ScalaFutures
  with TryValues
  with OptionValues
  with Eventually
  with PropertyChecks
  with ClasspathResources
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with GivenWhenThen
