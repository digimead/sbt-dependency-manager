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

package sbt.dependency

package object manager {
  /** Entry point for plugin in user's project */
  lazy val DependencyManager = Plugin.defaultSettings

  // export declarations for end user
  lazy val DMKey = Keys
  lazy val DMConf = Keys.DependencyConf

  // public keys
  def dependencyEnableCustom = Keys.dependencyEnableCustom
  def dependencyOutput = Keys.dependencyOutput
  def dependencyPackPath = Keys.dependencyPackPath
}
