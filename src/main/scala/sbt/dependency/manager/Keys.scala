/**
 * sbt-dependency-manager - fetch and merge byte code and source code jars, align broken sources within jars.
 * For example, it is allow easy source code lookup for IDE while developing SBT plugins (not only).
 *
 * Copyright (c) 2012-2014 Alexey Aksenov ezh@ezh.msk.ru
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sbt.dependency.manager

import java.util.zip.ZipEntry

import sbt._
import sbt.Keys._

object Keys {
  def DependencyConf = config("dependency") hide

  lazy val dependencyAdditionalArtifacts = TaskKey[Seq[(Option[java.io.File], Option[java.io.File])]]("dependencyAdditionalArtifacts", "Additional artifacts that are added to results.")
  lazy val dependencyFilter = TaskKey[Option[ModuleFilter]]("dependencyFilter", "Filter for project dependencies.")
  lazy val dependencyIgnoreConfigurations = SettingKey[Boolean]("dependencyIgnoreConfigurations", "Ignore configurations while lookup like 'test', for example.")
  lazy val dependencyOutput = SettingKey[Option[java.io.File]]("dependencyOutput", "Target directory for fetched artifacts. Fetch disabled if None.")
  lazy val dependencyOverwrite = SettingKey[Boolean]("dependencyOverwrite", "Overwrite exists artifacts.")
  lazy val dependencyPackPath = TaskKey[java.io.File]("dependencyPackPath", "Consolidated jar location.")
  lazy val dependencyPluginInfo = TaskKey[Unit]("dependencyPluginInfo", "Show plugin information.")
  lazy val dependencyResourceFilter = SettingKey[ZipEntry ⇒ Boolean]("dependencyResourceFilter", "Function for filtering jar content.")
  lazy val dependencySkipResolved = SettingKey[Boolean]("dependencySkipResolved", "Skip resolved dependencies with explicit artifacts which points to local resources.")
  lazy val dependencyTaskFetch = TaskKey[Unit]("dependencyFetch", "Fetch code jars. Save results to target directory.")
  lazy val dependencyTaskFetchAlign = TaskKey[Unit]("dependencyFetchAlign", "Fetch code and source jars, merge them. Save results to target directory.")
  lazy val dependencyTaskFetchWithSources = TaskKey[Unit]("dependencyFetchWithSources", "Fetch code and source jars. Save results to target directory.")
  lazy val dependencyTaskPack = TaskKey[Unit]("dependencyPack", "Fetch code and source jars. Save results to consolidated jar.")
}
