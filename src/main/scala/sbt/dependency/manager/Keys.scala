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
  def DependencyConf = config("dependency-manager") extend (Compile)

  lazy val dependencyBundlePath = TaskKey[java.io.File]("bundle-path", "Bundle jar location")
  lazy val dependencyClasspathFilter = TaskKey[ModuleFilter]("predefined-classpath-filter", "Predefined filter that accept all modules in project classpath.")
  lazy val dependencyEnable = SettingKey[Boolean]("enable", "Enable plugin tasks. Useful in multi project environment with nested dependencies.")
  lazy val dependencyEnableCustom = SettingKey[Boolean]("enable-custom-libraries", "Add custom(unknown) libraries to results")
  lazy val dependencyFilter = TaskKey[Option[ModuleFilter]]("filter", "Filtering dependencies with particular sbt.ModuleID")
  lazy val dependencyIgnoreConfiguration = SettingKey[Boolean]("ignore-configurations", "Ignore configurations while lookup like 'test', for example")
  lazy val dependencyLookupClasspath = TaskKey[Classpath]("lookup-classpath", "Classpath that is used for building the dependency sequence")
  lazy val dependencyPath = SettingKey[java.io.File]("path", "Target directory for fetched jars")
  lazy val dependencyResourceFilter = SettingKey[ZipEntry => Boolean]("resource-filter", "Function for filtering jar content.")
  lazy val dependencySkipResolved = SettingKey[Boolean]("skip-resolved", "Skip resolved dependencies with explicit artifacts which points to local resources")
  lazy val dependencyTaskBundle = TaskKey[Unit]("dependency-bundle", "Fetch dependency code and source jars. Save results to bundle.")
  lazy val dependencyTaskBundleWithArtifact = TaskKey[Unit]("dependency-bundle-with-artifact", "Fetch dependency code and source jars, add project artefact. Save results to bundle.")
  lazy val dependencyTaskFetch = TaskKey[Unit]("dependency-fetch", "Fetch dependency code jars. Save results to target directory")
  lazy val dependencyTaskFetchAlign = TaskKey[Unit]("dependency-fetch-align", "Fetch dependency code and source jars, merge them. Save results to target directory")
  lazy val dependencyTaskFetchWithSources = TaskKey[Unit]("dependency-fetch-with-sources", "Fetch dependency code and source jars. Save results to target directory")
}
