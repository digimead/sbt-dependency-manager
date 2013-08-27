/**
 * sbt-dependency-manager - fetch and merge byte code and source code jars, align broken sources within jars.
 * For example, it is allow easy source code lookup for IDE while developing SBT plugins (not only).
 *
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
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

  lazy val dependencyPackPath = TaskKey[java.io.File]("dependency-pack-path", "Consolidated jar location.")
  lazy val dependencyClasspathFilter = TaskKey[ModuleFilter]("dependency-predefined-classpath-filter", "Predefined filter that accept all modules in project classpath.")
  lazy val dependencyEnableCustom = SettingKey[Boolean]("dependency-enable-custom-libraries", "Add custom(unknown) libraries to the result.")
  lazy val dependencyFilter = TaskKey[Option[ModuleFilter]]("dependency-filter", "Filtering dependencies with particular sbt.ModuleID.")
  lazy val dependencyIgnoreConfiguration = SettingKey[Boolean]("dependency-ignore-configurations", "Ignore configurations while lookup like 'test', for example.")
  lazy val dependencyAdditionalArtifacts = TaskKey[Seq[(Option[java.io.File], Option[java.io.File])]]("dependency-additional-artifacts", "Additional artifacts that is added to results.")
  lazy val dependencyLookupClasspath = TaskKey[Classpath]("dependency-lookup-classpath", "Classpath that is used for building the dependency sequence.")
  lazy val dependencyOutput = SettingKey[Option[java.io.File]]("dependency-output", "Target directory for fetched artifacts. Fetch disabled if None.")
  lazy val dependencyPluginInfo = TaskKey[Unit]("dependency-plugin-info", "Show plugin information.")
  lazy val dependencyResourceFilter = SettingKey[ZipEntry => Boolean]("dependency-resource-filter", "Function for filtering jar content.")
  lazy val dependencySkipResolved = SettingKey[Boolean]("dependency-skip-resolved", "Skip resolved dependencies with explicit artifacts which points to local resources.")
  lazy val dependencyTaskPack = TaskKey[Unit]("dependency-pack", "Fetch dependency code and source jars. Save results to consolidated jar.")
  lazy val dependencyTaskFetch = TaskKey[Unit]("dependency-fetch", "Fetch dependency code jars. Save results to target directory.")
  lazy val dependencyTaskFetchAlign = TaskKey[Unit]("dependency-fetch-align", "Fetch dependency code and source jars, merge them. Save results to target directory.")
  lazy val dependencyTaskFetchWithSources = TaskKey[Unit]("dependency-fetch-with-sources", "Fetch dependency code and source jars. Save results to target directory.")
}
