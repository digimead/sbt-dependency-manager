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

package sbt.dependency

import sbt.Keys._
import sbt._

package object manager {
  /** Entry point for plugin in user's project */
  lazy val DependencyManager = Plugin.defaultSettings

  // export declarations for end user
  lazy val DMKey = Keys
  lazy val DMConf = Keys.DependencyConf

  // public keys
  /** Filter that accept all modules. */
  def DMFilterAcceptAll = (streams) map { _ ⇒
    val filter: Option[ModuleFilter] = Some(moduleFilter(AllPassFilter, AllPassFilter, AllPassFilter))
    filter
  }
  /** Filter that accept all modules except Scala library. */
  def DMFilterAcceptAllExceptScala = (streams) map { _ ⇒
    val filter: Option[ModuleFilter] = Some(moduleFilter(AllPassFilter, AllPassFilter, AllPassFilter) -
      moduleFilter(organization = GlobFilter("org.scala-lang"), name = GlobFilter("scala-library")))
    filter
  }
  /** Filter that accept only modules from libraryDependencies + dependencyLookupClasspath except Scala library. */
  def DMFilterAcceptKnown = (libraryDependencies in DMConf, dependencyClasspath in DMConf) map {
    (libraryDependencies, dependencyLookupClasspath) ⇒
      val classpathModules = dependencyLookupClasspath.flatMap(_.get(moduleID.key))
      val libraryModules = libraryDependencies.filterNot { m ⇒ classpathModules.exists(p ⇒ p.name == m.name && p.organization == m.organization && p.revision == m.revision) }
      val acceptKnown = (classpathModules ++ libraryModules).foldLeft(moduleFilter(NothingFilter, NothingFilter, NothingFilter))((acc, m) ⇒ acc |
        moduleFilter(GlobFilter(m.organization), GlobFilter(m.name), GlobFilter(m.revision)))
      val filter: Option[ModuleFilter] = Some(acceptKnown - moduleFilter(organization = GlobFilter("org.scala-lang"), name = GlobFilter("scala-library")))
      filter
  }
}
