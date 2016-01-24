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

import bintray.Plugin._

bintray.Plugin.bintraySettings

bintray.Keys.packageLabels in bintray.Keys.bintray := Seq("akka", "jdbc", "persistence")

bintray.Keys.packageAttributes in bintray.Keys.bintray ~=
  ((_: bintray.AttrMap) ++ Map("website_url" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-jdbc")), "github_repo" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-jdbc.git")), "issue_tracker_url" -> Seq(bintry.StringAttr("https://github.com/dnvriend/akka-persistence-jdbc/issues/"))))

publishMavenStyle := true