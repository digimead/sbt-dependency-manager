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

import java.io.{ BufferedOutputStream, ByteArrayOutputStream, File, FileInputStream, FileOutputStream, PrintWriter }
import java.net.URL
import java.util.{ Date, Properties }
import java.util.jar.{ JarInputStream, JarOutputStream }
import java.util.zip.{ ZipEntry, ZipException }
import sbt.{ Keys ⇒ sk, File ⇒ _, _ }
import sbt.Defaults
import sbt.DependencyFilter.subDepFilterToFn
import sbt.Keys._
import sbt.dependency.manager.Keys.DependencyConf
import scala.{ Left, Right }
import scala.collection.mutable.HashSet
import xsbti.AppConfiguration

/**
 * sbt-dependency-manager plugin entry
 */
object Plugin extends sbt.Plugin {
  implicit def option2rich[T](option: Option[T]): RichOption[T] = new RichOption(option)
  protected lazy val javaHome = new File(System.getProperty("java.home")).getCanonicalPath()
  def logPrefix(name: String) = "[Dep manager:%s] ".format(name)

  lazy val defaultSettings = inConfig(Keys.DependencyConf)(Seq(
    DMKey.dependencyAdditionalArtifacts := Seq(),
    DMKey.dependencyPackPath <<= (target, normalizedName) map { (target, name) ⇒ target / (name + "-development.jar") },
    DMKey.dependencyFilter <<= sbt.dependency.manager.DMFilterAcceptKnown,
    DMKey.dependencyIgnoreConfigurations := true,
    DMKey.dependencyLookupClasspath <<= Classpaths.concatDistinct(externalDependencyClasspath in Compile, externalDependencyClasspath in Test),
    DMKey.dependencyOutput <<= (target in ThisProject) { path ⇒ Some(path / "deps") },
    DMKey.dependencyOverwrite := false,
    DMKey.dependencyPluginInfo <<= dependencyPluginInfoTask,
    DMKey.dependencyResourceFilter := resourceFilter,
    DMKey.dependencySkipResolved := true,
    // add the empty classifier ""
    transitiveClassifiers in Global := Seq("", Artifact.SourceClassifier, Artifact.DocClassifier))) ++
    // global settings
    Seq(
      DMKey.dependencyTaskPack <<= dependencyTaskPackTask,
      DMKey.dependencyTaskFetch <<= dependencyTaskFetchTask,
      DMKey.dependencyTaskFetchAlign <<= dependencyTaskFetchAlignTask,
      DMKey.dependencyTaskFetchWithSources <<= dependencyTaskFetchWithSourcesTask)

  /** Show plugin information */
  def dependencyPluginInfoTask = (state, streams, thisProjectRef) map { (state, streams, thisProjectRef) ⇒
    val extracted: Extracted = Project.extract(state)
    val thisScope = Load.projectScope(thisProjectRef).copy(config = Select(DependencyConf))
    val name = (sbt.Keys.name in thisScope get extracted.structure.data) getOrElse thisProjectRef.project
    Option(getClass().getClassLoader().getResourceAsStream("version-sbt-dependency-manager.properties")) match {
      case Some(stream) ⇒
        val properties = new Properties()
        properties.load(stream)
        val date = new Date(properties.getProperty("build").toLong * 1000)
        streams.log.info(logPrefix(name) + "Name: " + properties.getProperty("name"))
        streams.log.info(logPrefix(name) + "Version: " + properties.getProperty("version"))
        streams.log.info(logPrefix(name) + "Build: " + date + " (" + properties.getProperty("build") + ")")
      case None ⇒
        streams.log.error(logPrefix(name) + "Dependency Mananger plugin information not found.")
    }
  }
  /** Implementation of dependency-pack */
  def dependencyTaskPackTask =
    (classifiersModule in updateSbtClassifiers, DMKey.dependencyPackPath in DependencyConf, DMKey.dependencyAdditionalArtifacts in DependencyConf,
      DMKey.dependencyFilter in DependencyConf, DMKey.dependencyLookupClasspath in DependencyConf, ivySbt, streams, state, thisProjectRef) map { (origClassifiersModule, pathPack,
        additionalArtifacts, dependencyFilter, dependencyClasspath, ivySbt, streams, state, thisProjectRef) ⇒
        val extracted: Extracted = Project.extract(state)
        val thisScope = Load.projectScope(thisProjectRef).copy(config = Select(DependencyConf))
        val name = (sbt.Keys.name in thisScope get extracted.structure.data) getOrElse thisProjectRef.project
        val output = DMKey.dependencyOutput in thisScope get extracted.structure.data getOrThrow "dependencyOutput is undefined"
        output match {
          case Some(dependencyOutput) ⇒
            streams.log.info(logPrefix(name) + "Fetch dependencies and align to consolidated jar.")
            val appConfiguration = sk.appConfiguration in thisScope get extracted.structure.data getOrThrow "appConfiguration is undefined"
            val ivyLoggingLevel = sk.ivyLoggingLevel in thisScope get extracted.structure.data getOrThrow "ivyLoggingLevel is undefined"
            val ivyScala = sk.ivyScala in thisScope get extracted.structure.data getOrThrow "ivyScala is undefined"
            val pathTarget = sk.target in thisScope get extracted.structure.data getOrThrow "pathTarget is undefined"
            val updateConfiguration = sk.updateConfiguration in thisScope get extracted.structure.data getOrThrow "updateConfiguration is undefined"
            val dependencyIgnoreConfiguration = DMKey.dependencyIgnoreConfigurations in thisScope get extracted.structure.data getOrThrow "dependencyIgnoreConfiguration is undefined"
            val dependencyOverwrite = DMKey.dependencyOverwrite in thisScope get extracted.structure.data getOrThrow "dependencyOverwrite is undefined"
            val dependencyResourceFilter = DMKey.dependencyResourceFilter in thisScope get extracted.structure.data getOrThrow "dependencyResourceFilter is undefined"
            val dependencySkipResolved = DMKey.dependencySkipResolved in thisScope get extracted.structure.data getOrThrow "dependencySkipResolved is undefined"
            val libraryDependenciesCompile = sbt.Keys.libraryDependencies in thisScope in Compile get extracted.structure.data getOrThrow "libraryDependencies in Compile is undefined"
            val libraryDependenciesTest = sbt.Keys.libraryDependencies in thisScope in Test get extracted.structure.data getOrThrow "libraryDependencies in Test is undefined"
            val libraryDependencies = (libraryDependenciesCompile ++ libraryDependenciesTest).distinct
            val argument = TaskArgument(appConfiguration, ivyLoggingLevel, ivySbt, ivyScala, libraryDependencies, name,
              origClassifiersModule, new UpdateConfiguration(updateConfiguration.retrieve, true, ivyLoggingLevel),
              pathPack, dependencyOutput, pathTarget, streams, None, additionalArtifacts, dependencyOverwrite, true, dependencyClasspath,
              dependencyFilter, dependencyIgnoreConfiguration, dependencyResourceFilter, dependencySkipResolved)
            commonFetchTask(argument, doFetchWithSources)
          case None ⇒
            streams.log.info(logPrefix(name) + "Fetch operation disabled")
        }
        () // Returns Unit. Return type isn't defined explicitly because it is different for different SBT versions.
      }
  /** Implementation of dependency-fetch-align */
  def dependencyTaskFetchAlignTask =
    (classifiersModule in updateSbtClassifiers, DMKey.dependencyPackPath in DependencyConf, DMKey.dependencyAdditionalArtifacts in DependencyConf,
      DMKey.dependencyFilter in DependencyConf, DMKey.dependencyLookupClasspath in DependencyConf, ivySbt, state, streams, thisProjectRef) map { (origClassifiersModule, pathPack,
        additionalArtifacts, dependencyFilter, dependencyClasspath, ivySbt, state, streams, thisProjectRef) ⇒
        val extracted: Extracted = Project.extract(state)
        val thisScope = Load.projectScope(thisProjectRef).copy(config = Select(DependencyConf))
        val name = (sbt.Keys.name in thisScope get extracted.structure.data) getOrElse thisProjectRef.project
        val output = DMKey.dependencyOutput in thisScope get extracted.structure.data getOrThrow "dependencyOutput is undefined"
        output match {
          case Some(dependencyOutput) ⇒
            streams.log.info(logPrefix(name) + "Fetch dependencies and align")
            val appConfiguration = sk.appConfiguration in thisScope get extracted.structure.data getOrThrow "appConfiguration is undefined"
            val ivyLoggingLevel = sk.ivyLoggingLevel in thisScope get extracted.structure.data getOrThrow "ivyLoggingLevel is undefined"
            val ivyScala = sk.ivyScala in thisScope get extracted.structure.data getOrThrow "ivyScala is undefined"
            val pathTarget = sk.target in thisScope get extracted.structure.data getOrThrow "pathTarget is undefined"
            val updateConfiguration = sk.updateConfiguration in thisScope get extracted.structure.data getOrThrow "updateConfiguration is undefined"
            val dependencyIgnoreConfiguration = DMKey.dependencyIgnoreConfigurations in thisScope get extracted.structure.data getOrThrow "dependencyIgnoreConfiguration is undefined"
            val dependencyOverwrite = DMKey.dependencyOverwrite in thisScope get extracted.structure.data getOrThrow "dependencyOverwrite is undefined"
            val dependencyResourceFilter = DMKey.dependencyResourceFilter in thisScope get extracted.structure.data getOrThrow "dependencyResourceFilter is undefined"
            val dependencySkipResolved = DMKey.dependencySkipResolved in thisScope get extracted.structure.data getOrThrow "dependencySkipResolved is undefined"
            val libraryDependenciesCompile = sbt.Keys.libraryDependencies in thisScope in Compile get extracted.structure.data getOrThrow "libraryDependencies in Compile is undefined"
            val libraryDependenciesTest = sbt.Keys.libraryDependencies in thisScope in Test get extracted.structure.data getOrThrow "libraryDependencies in Test is undefined"
            val libraryDependencies = (libraryDependenciesCompile ++ libraryDependenciesTest).distinct
            val argument = TaskArgument(appConfiguration, ivyLoggingLevel, ivySbt, ivyScala, libraryDependencies, name,
              origClassifiersModule, new UpdateConfiguration(updateConfiguration.retrieve, true, ivyLoggingLevel),
              pathPack, dependencyOutput, pathTarget, streams, None, additionalArtifacts, dependencyOverwrite, false, dependencyClasspath,
              dependencyFilter, dependencyIgnoreConfiguration, dependencyResourceFilter, dependencySkipResolved)
            commonFetchTask(argument, doFetchAlign)
          case None ⇒
            streams.log.info(logPrefix(name) + "Fetch operation disabled")
        }
        () // Returns Unit. Return type isn't defined explicitly because it is different for different SBT versions.
      }
  /** Implementation of dependency-fetch-with-sources */
  def dependencyTaskFetchWithSourcesTask =
    (classifiersModule in updateSbtClassifiers, DMKey.dependencyPackPath in DependencyConf, DMKey.dependencyAdditionalArtifacts in DependencyConf,
      DMKey.dependencyFilter in DependencyConf, DMKey.dependencyLookupClasspath in DependencyConf, ivySbt, state, streams, thisProjectRef) map { (origClassifiersModule, pathPack,
        additionalArtifacts, dependencyFilter, dependencyClasspath, ivySbt, state, streams, thisProjectRef) ⇒
        val extracted: Extracted = Project.extract(state)
        val thisScope = Load.projectScope(thisProjectRef).copy(config = Select(DependencyConf))
        val name = (sbt.Keys.name in thisScope get extracted.structure.data) getOrElse thisProjectRef.project
        val output = DMKey.dependencyOutput in thisScope get extracted.structure.data getOrThrow "dependencyOutput is undefined"
        output match {
          case Some(dependencyOutput) ⇒
            streams.log.info(logPrefix(name) + "Fetch dependencies with source code")
            val appConfiguration = sk.appConfiguration in thisScope get extracted.structure.data getOrThrow "appConfiguration is undefined"
            val ivyLoggingLevel = sk.ivyLoggingLevel in thisScope get extracted.structure.data getOrThrow "ivyLoggingLevel is undefined"
            val ivyScala = sk.ivyScala in thisScope get extracted.structure.data getOrThrow "ivyScala is undefined"
            val pathTarget = sk.target in thisScope get extracted.structure.data getOrThrow "pathTarget is undefined"
            val updateConfiguration = sk.updateConfiguration in thisScope get extracted.structure.data getOrThrow "updateConfiguration is undefined"
            val dependencyIgnoreConfiguration = DMKey.dependencyIgnoreConfigurations in thisScope get extracted.structure.data getOrThrow "dependencyIgnoreConfiguration is undefined"
            val dependencyOverwrite = DMKey.dependencyOverwrite in thisScope get extracted.structure.data getOrThrow "dependencyOverwrite is undefined"
            val dependencyResourceFilter = DMKey.dependencyResourceFilter in thisScope get extracted.structure.data getOrThrow "dependencyResourceFilter is undefined"
            val dependencySkipResolved = DMKey.dependencySkipResolved in thisScope get extracted.structure.data getOrThrow "dependencySkipResolved is undefined"
            val libraryDependenciesCompile = sbt.Keys.libraryDependencies in thisScope in Compile get extracted.structure.data getOrThrow "libraryDependencies in Compile is undefined"
            val libraryDependenciesTest = sbt.Keys.libraryDependencies in thisScope in Test get extracted.structure.data getOrThrow "libraryDependencies in Test is undefined"
            val libraryDependencies = (libraryDependenciesCompile ++ libraryDependenciesTest).distinct
            val argument = TaskArgument(appConfiguration, ivyLoggingLevel, ivySbt, ivyScala, libraryDependencies, name,
              origClassifiersModule, new UpdateConfiguration(updateConfiguration.retrieve, true, ivyLoggingLevel),
              pathPack, dependencyOutput, pathTarget, streams,
              None, additionalArtifacts, dependencyOverwrite, false, dependencyClasspath,
              dependencyFilter, dependencyIgnoreConfiguration, dependencyResourceFilter, dependencySkipResolved)
            commonFetchTask(argument, doFetchWithSources)
          case None ⇒
            streams.log.info(logPrefix(name) + "Fetch operation disabled")
        }
        () // Returns Unit. Return type isn't defined explicitly because it is different for different SBT versions.
      }
  /** Implementation of dependency-fetch */
  def dependencyTaskFetchTask =
    (classifiersModule in updateSbtClassifiers, DMKey.dependencyPackPath in DependencyConf, DMKey.dependencyAdditionalArtifacts in DependencyConf,
      DMKey.dependencyFilter in DependencyConf, DMKey.dependencyLookupClasspath in DependencyConf, ivySbt, state, streams, thisProjectRef) map {
        (origClassifiersModule, pathPack, additionalArtifacts, dependencyFilter, dependencyClasspath, ivySbt, state, streams, thisProjectRef) ⇒
          val extracted: Extracted = Project.extract(state)
          val thisScope = Load.projectScope(thisProjectRef).copy(config = Select(DependencyConf))
          val name = (sbt.Keys.name in thisScope get extracted.structure.data) getOrElse thisProjectRef.project
          val output = DMKey.dependencyOutput in thisScope get extracted.structure.data getOrThrow "dependencyOutput is undefined"
          output match {
            case Some(dependencyOutput) ⇒
              streams.log.info(logPrefix(name) + "Fetch dependencies")
              val appConfiguration = sk.appConfiguration in thisScope get extracted.structure.data getOrThrow "appConfiguration is undefined"
              val ivyLoggingLevel = sk.ivyLoggingLevel in thisScope get extracted.structure.data getOrThrow "ivyLoggingLevel is undefined"
              val ivyScala = sk.ivyScala in thisScope get extracted.structure.data getOrThrow "ivyScala is undefined"
              val pathTarget = sk.target in thisScope get extracted.structure.data getOrThrow "pathTarget is undefined"
              val updateConfiguration = sk.updateConfiguration in thisScope get extracted.structure.data getOrThrow "updateConfiguration is undefined"
              val dependencyIgnoreConfiguration = DMKey.dependencyIgnoreConfigurations in thisScope get extracted.structure.data getOrThrow "dependencyIgnoreConfiguration is undefined"
              val dependencyOverwrite = DMKey.dependencyOverwrite in thisScope get extracted.structure.data getOrThrow "dependencyOverwrite is undefined"
              val dependencyResourceFilter = DMKey.dependencyResourceFilter in thisScope get extracted.structure.data getOrThrow "dependencyResourceFilter is undefined"
              val dependencySkipResolved = DMKey.dependencySkipResolved in thisScope get extracted.structure.data getOrThrow "dependencySkipResolved is undefined"
              val libraryDependenciesCompile = sbt.Keys.libraryDependencies in thisScope in Compile get extracted.structure.data getOrThrow "libraryDependencies in Compile is undefined"
              val libraryDependenciesTest = sbt.Keys.libraryDependencies in thisScope in Test get extracted.structure.data getOrThrow "libraryDependencies in Test is undefined"
              val libraryDependencies = (libraryDependenciesCompile ++ libraryDependenciesTest).distinct
              val argument = TaskArgument(appConfiguration, ivyLoggingLevel, ivySbt, ivyScala, libraryDependencies, name,
                origClassifiersModule, new UpdateConfiguration(updateConfiguration.retrieve, true, ivyLoggingLevel),
                pathPack, dependencyOutput, pathTarget, streams, None, additionalArtifacts, dependencyOverwrite, false, dependencyClasspath,
                dependencyFilter, dependencyIgnoreConfiguration, dependencyResourceFilter, dependencySkipResolved)
              commonFetchTask(argument, doFetch)
            case None ⇒
              streams.log.info(logPrefix(name) + "Fetch operation disabled")
          }
          () // Returns Unit. Return type isn't defined explicitly because it is different for different SBT versions.
      }
  /**
   * Dependency resource filter
   * It drops META-INF/ .SF .DSA .RSA files by default
   */
  def resourceFilter(entry: ZipEntry): Boolean =
    Seq("META-INF/.*\\.SF", "META-INF/.*\\.DSA", "META-INF/.*\\.RSA").find(entry.getName().toUpperCase().matches).nonEmpty

  /** Repack sequence of jar artifacts */
  protected def align(arg: TaskArgument, moduleTag: String, code: File, sources: File, targetDirectory: File, resourceFilter: ZipEntry ⇒ Boolean, s: TaskStreams,
    alignEntries: HashSet[String] = HashSet[String](), output: JarOutputStream = null): Unit = {
    if (!targetDirectory.exists())
      if (!targetDirectory.mkdirs())
        return s.log.error(logPrefix(arg.name) + "Unable to create " + targetDirectory)
    val target = new File(targetDirectory, code.getName)
    if (!arg.dependencyOverwrite && target.exists())
      return s.log.info(logPrefix(arg.name) + "Artifact " + target.getName() + " is already exists")
    if (output == null) {
      s.log.info(logPrefix(arg.name) + "Fetch and align " + moduleTag)
      s.log.debug(logPrefix(arg.name) + "Save result to " + target.getAbsoluteFile())
    } else
      s.log.info(logPrefix(arg.name) + "Fetch and align " + moduleTag + ", target: consolidated jar.")
    // align
    var jarCode: JarInputStream = null
    var jarSources: JarInputStream = null
    var jarTarget: JarOutputStream = Option(output) getOrElse null
    try {
      jarCode = new JarInputStream(new FileInputStream(code))
      jarSources = new JarInputStream(new FileInputStream(sources))
      if (jarTarget == null && output == null) {
        if (target.exists())
          if (!target.delete()) {
            try {
              jarCode.close
              jarSources.close
            } catch {
              case e: Throwable ⇒
            }
            return s.log.error(logPrefix(arg.name) + "Unable to delete " + target)
          }
        jarTarget = try {
          new JarOutputStream(new BufferedOutputStream(new FileOutputStream(target, true)), jarCode.getManifest())
        } catch {
          case e: NullPointerException ⇒
            s.log.warn(logPrefix(arg.name) + code + " has broken manifest")
            new JarOutputStream(new BufferedOutputStream(new FileOutputStream(target, true)))
        }
      }
      // copy across all entries from the original code jar
      s.log.debug(logPrefix(arg.name) + s"Append original content from ${code.getName()} to consolidated ${target.getName}")
      copy(arg, alignEntries, jarCode, jarTarget, s, target.getName)
      // copy across all entries from the original sources jar
      s.log.debug(logPrefix(arg.name) + s"Append source code from ${sources.getName()} to consolidated ${target.getName}")
      copy(arg, alignEntries, jarSources, jarTarget, s, target.getName)
    } catch {
      case e: Throwable ⇒
        s.log.error(logPrefix(arg.name) + "Unable to align: " + e.getClass().getName() + " " + e.getMessage())
    } finally {
      if (jarTarget != null && output == null) {
        jarTarget.flush()
        jarTarget.close()
      }
      if (jarCode != null)
        jarCode.close()
      if (jarSources != null)
        jarSources.close()
    }
  }
  /** Common part for all sbt-dependency-manager tasks */
  protected def commonFetchTask(arg: TaskArgument, userFunction: (TaskArgument, Seq[(sbt.ModuleID, File)], Seq[(sbt.ModuleID, File)]) ⇒ Seq[sbt.ModuleID]): UpdateReport =
    synchronized {
      IO.withTemporaryDirectory { tempDirectory ⇒
        Classpaths.withExcludes(arg.pathTarget, arg.origClassifiersModule.classifiers, Defaults.lock(arg.appConfiguration)) { excludes ⇒
          import arg.origClassifiersModule.{ id ⇒ origClassifiersModuleID, modules ⇒ origClassifiersModuleDeps }
          arg.streams.log.debug(logPrefix(arg.name) + "Fetch dependencies to " + arg.pathDependency)
          if (arg.dependencyPack)
            arg.streams.log.info(logPrefix(arg.name) + "Create consolidated jar " + arg.pathPack)
          // do default update-sbt-classifiers with libDeps
          // Get all dependencyClasspath artifacts without explicit uri
          val empty = (List[ModuleID](), List[ModuleID]())
          def toLeft[L, R](acc: (List[L], List[R]))(l: L) = (l :: acc._1, acc._2)
          def toRight[L, R](acc: (List[L], List[R]))(r: R) = (acc._1, r :: acc._2)
          val (explicitDependencies, dependencies) = arg.dependencyClasspath.sortBy(_.toString).
            map(classpath ⇒ classpath.get(moduleID.key) match {
              case Some(dModule) ⇒
                arg.libraryDependencies.find { lModule ⇒
                  dModule.name == lModule.name && dModule.organization == lModule.organization &&
                    dModule.revision == lModule.revision && lModule.explicitArtifacts.exists { artifact ⇒
                      artifact.url.nonEmpty && artifact.classifier.isEmpty
                    }
                } match {
                  case Some(lModule) ⇒
                    Some(Left(lModule)) // ModuleID with explicit artifact
                  case None ⇒
                    Some(Right(dModule))
                }
              case None ⇒
                if (classpath.data.getCanonicalPath().startsWith(javaHome))
                  None
                else
                  Some(Left("UNKNOWN" % "UNKNOWN" % "UNKNOWN" from classpath.data.toURI().toASCIIString()))
            }).flatten.foldLeft(empty) { (acc, a) ⇒ a.fold(toLeft(acc), toRight(acc)) }
          // Apply arg.dependencyFilter to (origClassifiersModuleDeps ++ dependencies)
          val filteredDependencies = {
            val all = arg.dependencyFilter match {
              case Some(filter) ⇒ (origClassifiersModuleDeps ++ dependencies).filter(filter)
              case None ⇒ (origClassifiersModuleDeps ++ dependencies)
            }
            if (arg.dependencyIgnoreConfiguration)
              all.map(_.copy(configurations = None))
            else
              all
          }
          // Apply arg.dependencyFilter to explicitDependencies
          val filteredExplicitDependencies = {
            val all = arg.dependencyFilter match {
              case Some(filter) ⇒ explicitDependencies.filter(filter)
              case None ⇒ explicitDependencies
            }
            if (arg.dependencyIgnoreConfiguration)
              all.map(_.copy(configurations = None))
            else
              all
          }
          arg.streams.log.debug(logPrefix(arg.name) + "Detected dependencies: " + filteredDependencies.mkString(","))
          arg.streams.log.debug(logPrefix(arg.name) + "Detected explicit dependencies: " + filteredExplicitDependencies.mkString(","))
          val customConfig = GetClassifiersConfiguration(arg.origClassifiersModule, excludes, arg.updateConfiguration, arg.ivyScala)
          val customBaseModuleID = restrictedCopy(origClassifiersModuleID, true).copy(name = origClassifiersModuleID.name + "$sbt")
          val customIvySbtModule = new arg.ivySbt.Module(InlineConfiguration(customBaseModuleID, ModuleInfo(customBaseModuleID.name), filteredDependencies).copy(ivyScala = arg.ivyScala))
          val customUpdateReport = IvyActions.update(customIvySbtModule, arg.updateConfiguration, arg.streams.log)
          val newConfig = customConfig.copy(module = arg.origClassifiersModule.copy(modules = customUpdateReport.allModules))
          val updateReport = IvyActions.updateClassifiers(arg.ivySbt, newConfig, arg.streams.log)
          // process updateReport
          // Get all sources
          val (sources, other) = updateReport.toSeq.partition {
            case (_, _, Artifact(_, _, _, Some(Artifact.SourceClassifier), _, _, _), _) ⇒ true
            case _ ⇒ false
          }
          val sourceObjects = sources.map { case (configuration, moduleId, artifact, file) ⇒ (moduleId, file) }
          // Apply arg.dependencyFilter 2nd time
          val codeObjects = (arg.dependencyFilter match {
            case Some(filter) ⇒ other.filter {
              case (configuration, moduleId, artifact, file) ⇒
                val result = filter(moduleId)
                if (!result)
                  arg.streams.log.debug("filter " + moduleId)
                result
            }
            case None ⇒ other
          }).map {
            case (configuration, moduleId, artifact, file) if artifact.classifier == None || artifact.classifier == Some("") ⇒
              Some((moduleId, file))
            case _ ⇒
              None
          }.flatten

          // Apply userFunction to primary dependencies
          val primary = userFunction(arg, sourceObjects, codeObjects)

          // Apply userFunction to additional dependencies
          explicitDependencies.filterNot { m ⇒ primary.exists(p ⇒ p.name == m.name && p.organization == m.organization && p.revision == m.revision) }.
            foreach { moduleId ⇒
              val codeArtifact = moduleId.explicitArtifacts.find(_.classifier == None)
              val sourceCodeArtifact = moduleId.explicitArtifacts.find(_.classifier == Some(Artifact.SourceClassifier))
              (codeArtifact, sourceCodeArtifact) match {
                case (Some(Artifact(_, _, _, _, _, Some(codeURL), _)), Some(Artifact(_, _, _, _, _, Some(sourceCodeURL), _))) ⇒
                  val targetURLPath = codeURL.getPath()
                  val targetName = targetURLPath.substring(targetURLPath.lastIndexOf('/') + 1, targetURLPath.length())
                  if (!arg.dependencyOverwrite && !arg.dependencyPack &&
                    (new File(arg.pathDependency, targetName)).exists()) {
                    arg.streams.log.info(logPrefix(arg.name) + "Artifact " + targetName + " is already exists")
                  } else {
                    val code = getArtifact(arg, codeURL, tempDirectory)
                    val source = getArtifact(arg, sourceCodeURL, tempDirectory)
                    code match {
                      case Some(code) ⇒
                        source match {
                          case Some(source) ⇒
                            userFunction(arg, Seq((moduleId, source)), Seq((moduleId, code)))
                          case None ⇒
                            arg.streams.log.warn(logPrefix(arg.name) + "Unable to aquire source code artifact for module " + moduleId)
                            userFunction(arg, Seq(), Seq((moduleId, code)))
                        }
                      case None ⇒
                        arg.streams.log.error(logPrefix(arg.name) + "Unable to aquire artifact for module " + moduleId)
                    }
                  }
                case (Some(Artifact(_, _, _, _, _, Some(codeURL), _)), _) ⇒
                  val targetURLPath = codeURL.getPath()
                  val targetName = targetURLPath.substring(targetURLPath.lastIndexOf('/') + 1, targetURLPath.length())
                  if (!arg.dependencyOverwrite && !arg.dependencyPack &&
                    (new File(arg.pathDependency, targetName)).exists()) {
                    arg.streams.log.info(logPrefix(arg.name) + "Artifact " + targetName + " is already exists")
                  } else {
                    val code = getArtifact(arg, codeURL, tempDirectory)
                    code match {
                      case Some(code) ⇒
                        arg.streams.log.info(logPrefix(arg.name) + "Fetch custom library " + code.getName())
                        if (arg.dependencyPack) {
                          copyToCodePack(arg, code)
                          copyToSourcePack(arg, code)
                        } else
                          sbt.IO.copyFile(code, new File(arg.pathDependency, code.getName()), false)
                      case None ⇒
                        arg.streams.log.error(logPrefix(arg.name) + "Unable to aquire artifact for module " + moduleId)
                    }
                  }
                case _ ⇒
                  arg.streams.log.error(logPrefix(arg.name) + "Unable to aquire artifacts for module " + moduleId)
              }
            }
          arg.dependencyAdditionlArtifacts.foreach {
            case (codeArtifact, sourceCodeArtifact) ⇒
              if (arg.dependencyPack) {
                arg.streams.log.info(logPrefix(arg.name) + "Fetch additional library " +
                  (codeArtifact orElse sourceCodeArtifact map (_.getName()) getOrElse (codeArtifact, sourceCodeArtifact)))
                codeArtifact.foreach(copyToCodePack(arg, _))
                sourceCodeArtifact.foreach(copyToSourcePack(arg, _))
              } else {
                (codeArtifact, sourceCodeArtifact) match {
                  case (Some(code), Some(source)) ⇒
                    val moduleId = sbt.ModuleID("Unknown", code.getName, System.currentTimeMillis().toString)
                    userFunction(arg, Seq((moduleId, source)), Seq((moduleId, code)))
                  case (Some(code), None) ⇒
                    arg.streams.log.info(logPrefix(arg.name) + "Fetch additional library " + code.getName())
                    sbt.IO.copyFile(code, new File(arg.pathDependency, code.getName()), false)
                  case _ ⇒
                    arg.streams.log.error(logPrefix(arg.name) + "Unable to aquire additional artifacts: " + (codeArtifact, sourceCodeArtifact))
                }
              }
          }
          if (arg.dependencyPack) {
            // add artifact
            arg.dependencyArtifact.foreach(copyToCodePack(arg, _))
            arg.dependencyArtifact.foreach(copyToSourcePack(arg, _))
            arg.packWithCode.flush()
            arg.packWithCode.close()
            arg.packWithSource.flush()
            arg.packWithSource.close()
            // create consolidated jar description
            val directory = arg.pathPack.getParentFile()
            val file = arg.pathPack.getName() + ".description"
            val descriptionFile = new File(directory, file)
            Some(new PrintWriter(descriptionFile)).foreach { writer ⇒
              try {
                writer.write(arg.packResources.toList.sorted.mkString("\n"))
              } catch {
                case e: Throwable ⇒
                  arg.streams.log.error(logPrefix(arg.name) + "Unable to create consolidated jar description " + descriptionFile.getAbsolutePath() + " " + e)
              } finally {
                try { writer.close } catch { case e: Throwable ⇒ }
              }
            }
          }
          updateReport
        }
      }
    }
  /** Specific part for tasks dependency-fetch-align, dependency-pack, dependency-pack-with-artifact */
  protected def doFetchAlign(arg: TaskArgument, sourceObjects: Seq[(sbt.ModuleID, File)],
    codeObjects: Seq[(sbt.ModuleID, File)]): Seq[sbt.ModuleID] = codeObjects.map {
    case (module, codeJar) ⇒
      sourceObjects.find(source ⇒ source._1 == module) match {
        case Some((_, sourceJar)) ⇒
          if (arg.dependencyPack) {
            align(arg, module.toString, codeJar, sourceJar, arg.pathDependency, resourceFilter, arg.streams, arg.packEntries, arg.packWithCode)
            arg.packResources += codeJar.getAbsolutePath()
          } else
            align(arg, module.toString, codeJar, sourceJar, arg.pathDependency, resourceFilter, arg.streams)
        case None ⇒
          arg.streams.log.debug(logPrefix(arg.name) + "Skip align for dependency " + module + " - sources not found ")
          if (arg.dependencyPack) {
            arg.streams.log.info(logPrefix(arg.name) + "Add " + module + " to consolidated jar without source code")
            copyToCodePack(arg, codeJar)
          } else {
            arg.streams.log.info(logPrefix(arg.name) + "Fetch " + module + " without source code")
            val codeTarget = new File(arg.pathDependency, codeJar.getName())
            arg.streams.log.debug(logPrefix(arg.name) + "Save result to " + codeTarget.getAbsolutePath())
            sbt.IO.copyFile(codeJar, codeTarget, false)
          }
      }
      module
  }
  /** Specific part for task dependency-fetch-with-sources */
  protected def doFetchWithSources(arg: TaskArgument, sourceObjects: Seq[(sbt.ModuleID, File)],
    codeObjects: Seq[(sbt.ModuleID, File)]): Seq[sbt.ModuleID] = codeObjects.map {
    case (module, codeJar) ⇒
      sourceObjects.find(source ⇒ source._1 == module) match {
        case Some((_, sourceJar)) ⇒
          if (arg.dependencyPack) {
            arg.streams.log.info(logPrefix(arg.name) + "Fetch with source code " + module + ", target: consolidated jar.")
            copyToCodePack(arg, codeJar)
            copyToSourcePack(arg, sourceJar)
            arg.packResources += codeJar.getAbsolutePath()
          } else {
            val codeTarget = new File(arg.pathDependency, codeJar.getName())
            val sourceTarget = new File(arg.pathDependency, sourceJar.getName())
            arg.streams.log.info(logPrefix(arg.name) + "Fetch with source code " + module)
            arg.streams.log.debug(logPrefix(arg.name) + "Save results to " + codeTarget.getParentFile.getAbsolutePath())
            sbt.IO.copyFile(codeJar, codeTarget, false)
            sbt.IO.copyFile(sourceJar, sourceTarget, false)
          }
        case None ⇒
          if (arg.dependencyPack) {
            arg.streams.log.info(logPrefix(arg.name) + "Fetch with source code " + module + ", target: consolidated jar.")
            copyToCodePack(arg, codeJar)
          } else {
            arg.streams.log.info(logPrefix(arg.name) + "Fetch with source code " + module)
            val codeTarget = new File(arg.pathDependency, codeJar.getName())
            arg.streams.log.debug(logPrefix(arg.name) + "Save results to " + codeTarget.getParentFile.getAbsolutePath())
            sbt.IO.copyFile(codeJar, codeTarget, false)
          }
      }
      module
  }
  /** Specific part for task dependency-fetch */
  protected def doFetch(arg: TaskArgument, sourceObjects: Seq[(sbt.ModuleID, File)],
    codeObjects: Seq[(sbt.ModuleID, File)]): Seq[sbt.ModuleID] = codeObjects.flatMap {
    case (module, codeJar) ⇒
      sourceObjects.find(source ⇒ source._1 == module) match {
        case Some((_, sourceJar)) ⇒
          arg.streams.log.info(logPrefix(arg.name) + "Fetch " + module)
          val codeTarget = new File(arg.pathDependency, codeJar.getName())
          arg.streams.log.debug(logPrefix(arg.name) + "Save result to " + codeTarget.getAbsolutePath())
          sbt.IO.copyFile(codeJar, codeTarget, false)
          Some(module)
        case None ⇒
          arg.streams.log.debug(logPrefix(arg.name) + "Skip " + module)
          None
      }
  }
  /** Return local artifact. */
  protected def getArtifact(arg: TaskArgument, artifact: URL, container: File): Option[File] =
    if (artifact.getProtocol() == "file") {
      sbt.IO.urlAsFile(artifact)
    } else {
      val targetURLPath = artifact.getPath()
      val targetName = targetURLPath.substring(targetURLPath.lastIndexOf('/') + 1, targetURLPath.length())
      val artifactFile = new File(container, targetName)
      try {
        arg.streams.log.info(logPrefix(arg.name) + "Download " + artifact)
        sbt.IO.download(artifact, artifactFile)
        Some(artifactFile)
      } catch {
        case e: Throwable ⇒
          arg.streams.log.error(logPrefix(arg.name) + "Unable to download " + artifact + ": " + e.getMessage())
          artifactFile.delete()
          None
      }
    }
  /** Repack content of jar artifact */
  private def alignScalaSource(arg: TaskArgument, alignEntries: HashSet[String], entry: ZipEntry, content: String, s: TaskStreams): Option[ZipEntry] = {
    val searchFor = "/" + entry.getName.takeWhile(_ != '.')
    val distance = alignEntries.toSeq.map(path ⇒ (path.indexOf(searchFor), path)).filter(_._1 > 1).sortBy(_._1).headOption
    distance match {
      case Some((idx, entryPath)) ⇒
        val newEntry = new ZipEntry(entryPath.substring(0, idx) + searchFor + ".scala")
        s.log.debug(logPrefix(arg.name) + "Align " + entry.getName + " to " + newEntry.getName())
        newEntry.setComment(entry.getComment())
        newEntry.setCompressedSize(entry.getCompressedSize())
        newEntry.setCrc(entry.getCrc())
        newEntry.setExtra(entry.getExtra())
        newEntry.setMethod(entry.getMethod())
        newEntry.setSize(entry.getSize())
        newEntry.setTime(entry.getTime())
        Some(newEntry)
      case None ⇒
        var path = Seq[String]()
        val pattern = """\s*package\s+([a-z\\._$-]+).*""".r
        content.split("\n").foreach {
          case pattern(packageName) ⇒
            path = path :+ packageName.replaceAll("\\.", "/")
          case line ⇒
        }
        if (path.nonEmpty) {
          val prefix = path.mkString("/") + "/"
          alignEntries.toSeq.find(_.startsWith(prefix)) match {
            case Some(path) ⇒
              val newEntry = new ZipEntry(prefix + entry.getName())
              s.log.debug(logPrefix(arg.name) + "Align " + entry.getName + " to " + newEntry.getName())
              newEntry.setComment(entry.getComment())
              newEntry.setCompressedSize(entry.getCompressedSize())
              newEntry.setCrc(entry.getCrc())
              newEntry.setExtra(entry.getExtra())
              newEntry.setMethod(entry.getMethod())
              newEntry.setSize(entry.getSize())
              newEntry.setTime(entry.getTime())
              Some(newEntry)
            case None ⇒
              s.log.warn(logPrefix(arg.name) + "Failed to align source " + entry.getName())
              None
          }
        } else
          None
    }
  }
  /** Copy content of jar artifact */
  private def copy(arg: TaskArgument, alignEntries: HashSet[String], in: JarInputStream, out: JarOutputStream, s: TaskStreams, jarName: String) {
    var entry: ZipEntry = null
    // copy across all entries from the original code jar
    var value: Int = 0
    try {
      val buffer = new Array[Byte](2048)
      entry = in.getNextEntry()
      while (entry != null) {
        if (alignEntries(entry.getName)) {
          if (!entry.isDirectory())
            s.log.debug(logPrefix(arg.name) + s"Skip entry '${entry.getName()}'. It is already exists in " + jarName)
        } else if (arg.dependencyResourceFilter(entry)) {
          s.log.debug(logPrefix(arg.name) + "Skip filtered entry " + entry.getName())
        } else
          try {
            alignEntries(entry.getName) = true
            val bos = new ByteArrayOutputStream()
            value = in.read(buffer)
            while (value > 0) {
              bos.write(buffer, 0, value)
              value = in.read(buffer)
            }
            val destEntry = new ZipEntry(entry.getName)
            out.putNextEntry(destEntry)
            out.write(bos.toByteArray())
            // adjust root scala sources
            if (entry.getName.endsWith(".scala") && entry.getName.indexOf("/") == -1)
              alignScalaSource(arg, alignEntries, entry, bos.toString, s).foreach {
                entry ⇒
                  if (alignEntries(entry.getName)) {
                    if (!entry.isDirectory())
                      s.log.debug(logPrefix(arg.name) + s"Skip entry '${entry.getName()}'. It is already exists in " + jarName)
                  } else {
                    out.putNextEntry(entry)
                    out.write(bos.toByteArray())
                  }
              }
          } catch {
            case e: ZipException ⇒
              s.log.error(logPrefix(arg.name) + "Zip failed: " + e.getMessage())
          }
        entry = in.getNextEntry()
      }
    } catch {
      case e: Throwable ⇒
        s.log.error(logPrefix(arg.name) + "Copy failed: " + e.getClass().getName() + " " + e.getMessage())
    }
  }
  /** Copy content to consolidated jar */
  private def copyToCodePack(arg: TaskArgument, codeJar: File) {
    arg.streams.log.debug(logPrefix(arg.name) + "Append %s to consolidated jar with compiled classes.".format(codeJar.getName()))
    // copy across all entries from the original code jar
    val jarCode = new JarInputStream(new FileInputStream(codeJar))
    try {
      copy(arg, arg.packEntries, jarCode, arg.packWithCode, arg.streams, arg.pathPack.getName())
      arg.packResources += codeJar.getAbsolutePath()
    } catch {
      case e: Throwable ⇒
        arg.streams.log.error(logPrefix(arg.name) + "Unable to merge: " + e.getClass().getName() + " " + e.getMessage())
    } finally {
      if (jarCode != null)
        jarCode.close()
    }
  }
  /** Copy content to consolidated jar  */
  private def copyToSourcePack(arg: TaskArgument, sourceJar: File) {
    arg.streams.log.debug("append %s to consolidated jar with source code.".format(sourceJar.getName()))
    // copy across all entries from the original code jar
    val jarSource = new JarInputStream(new FileInputStream(sourceJar))
    try {
      copy(arg, arg.packEntries, jarSource, arg.packWithSource, arg.streams, arg.pathPack.getName())
    } catch {
      case e: Throwable ⇒
        arg.streams.log.error(logPrefix(arg.name) + "Unable to merge: " + e.getClass().getName() + " " + e.getMessage())
    } finally {
      if (jarSource != null)
        jarSource.close()
    }
  }
  private[this] def restrictedCopy(m: ModuleID, confs: Boolean) =
    ModuleID(m.organization, m.name, m.revision, crossVersion = m.crossVersion, extraAttributes = m.extraAttributes, configurations = if (confs) m.configurations else None)

  /** Consolidated argument with all required information. */
  case class TaskArgument(
    /** Application configuration that provides information about SBT process. */
    appConfiguration: AppConfiguration,
    /** The property representing Ivy process log level. */
    ivyLogLevel: UpdateLogging.Value,
    /** Ivy wrapper that contains org.apache.ivy.Ivy and org.apache.ivy.core.settings.IvySettings. */
    ivySbt: IvySbt,
    /** Ivy scala artifacts description. */
    ivyScala: Option[IvyScala],
    /** Original ModuleIDs from SBT project definition. */
    libraryDependencies: Seq[ModuleID],
    /** Current project name. */
    name: String,
    /** GetClassifiersModule. */
    origClassifiersModule: GetClassifiersModule,
    /** Update configuration. */
    updateConfiguration: UpdateConfiguration,
    /** Path to consolidated jar with file name. */
    pathPack: java.io.File,
    /** Path to Fetched artifacts. */
    pathDependency: java.io.File,
    /** Target path. */
    pathTarget: java.io.File,
    /** SBT task streams for logging. */
    streams: TaskStreams,
    /** The property representing artifact location. */
    dependencyArtifact: Option[java.io.File],
    /** The property representing additional artifacts (code, source) that included into result. */
    dependencyAdditionlArtifacts: Seq[(Option[java.io.File], Option[java.io.File])],
    /** Overwrite artifact if exists. */
    dependencyOverwrite: Boolean,
    /** Flag indicating whether plugin should create consolidated jar. */
    dependencyPack: Boolean,
    /** Classpath that is used to build dependency sequence. */
    dependencyClasspath: Classpath,
    /** Fetch filter. */
    dependencyFilter: Option[ModuleFilter],
    /** Flag indicating whether plugin should ignore a dependency configuration while lookup ('test' for example). */
    dependencyIgnoreConfiguration: Boolean,
    /** Function that filters jar content. */
    dependencyResourceFilter: ZipEntry ⇒ Boolean,
    /** Skip resolved dependencies with explicit artifacts which points to local resources. */
    dependencySkipResolved: Boolean) {
    /** Output stream for consolidated jar with compiled code. */
    val packWithCode: JarOutputStream = if (dependencyPack) {
      assert(pathPack.name endsWith ".jar", "incorrect dependency-pack-path, must be path to jar file")
      pathPack.delete() // remove old pack
      new JarOutputStream(new BufferedOutputStream(new FileOutputStream(pathPack, true)))
    } else
      null
    /** Output stream for consolidated jar with source code. */
    val packWithSource: JarOutputStream = if (dependencyPack) {
      assert(pathPack.name endsWith ".jar", "incorrect dependency-pack-path, must be path to jar file")
      val directory = pathPack.getParentFile()
      val name = pathPack.getName
      val pathSourcePack = new File(directory, name.replaceFirst(""".jar$""", """-sources.jar"""))
      pathSourcePack.delete() // remove old pack
      new JarOutputStream(new BufferedOutputStream(new FileOutputStream(pathSourcePack, true)))
    } else
      null
    val packEntries = HashSet[String]()
    val packResources = HashSet[String]()
  }
  class RichOption[T](option: Option[T]) {
    def getOrThrow(onError: String) = option getOrElse { throw new NoSuchElementException(onError) }
  }
}
