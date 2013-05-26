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

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintWriter
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException

import scala.Option.option2Iterable
import scala.collection.mutable.HashSet

import sbt._
import sbt.Keys._
import sbt.dependency.manager.Keys._
import xsbti.AppConfiguration

/**
 * sbt-dependency-manager plugin entry
 */
object Plugin extends sbt.Plugin {
  def logPrefix(name: String) = "[Dep manager:%s] ".format(name)

  lazy val defaultSettings = inConfig(Keys.DependencyConf)(Seq(
    dependencyEnableCustom := true,
    dependencyBundlePath <<= (target, normalizedName) map { (target, name) => target / (name + "-development-bundle.jar") },
    dependencyClasspathFilter <<= (dependencyLookupClasspathTask) map (cp =>
      cp.flatMap(_.get(moduleID.key)).foldLeft(moduleFilter(NothingFilter, NothingFilter, NothingFilter))((acc, m) => acc |
        moduleFilter(GlobFilter(m.organization), GlobFilter(m.name), GlobFilter(m.revision)))),
    dependencyFilter <<= dependencyClasspathFilter map (dcf => Some(dcf -
      moduleFilter(organization = GlobFilter("org.scala-lang"), name = GlobFilter("scala-library")))),
    dependencyIgnoreConfiguration := true,
    dependencyLookupClasspath <<= dependencyLookupClasspathTask,
    dependencyPath <<= (target in LocalRootProject) { _ / "deps" },
    dependencyResourceFilter := resourceFilter,
    dependencySkipResolved := true,
    // add the empty classifier ""
    transitiveClassifiers in Global := Seq("", Artifact.SourceClassifier, Artifact.DocClassifier))) ++
    // global settings
    Seq(
      dependencyTaskBundle <<= dependencyTaskBundleTask,
      dependencyTaskBundleWithArtifact <<= dependencyTaskBundleWithArtifactTask,
      dependencyTaskFetch <<= dependencyTaskFetchTask,
      dependencyTaskFetchAlign <<= dependencyTaskFetchAlignTask,
      dependencyTaskFetchWithSources <<= dependencyTaskFetchWithSourcesTask)

  /** Implementation of dependency-bundle */
  def dependencyTaskBundleTask =
    (classifiersModule in updateSbtClassifiers, dependencyBundlePath in DependencyConf, dependencyFilter in DependencyConf,
      dependencyLookupClasspath in DependencyConf, ivySbt, streams, state, thisProjectRef) map { (origClassifiersModule, pathBundle,
        dependencyFilter, dependencyClasspath, ivySbt, streams, state, thisProjectRef) =>
        val extracted: Extracted = Project.extract(state)
        val thisScope = Load.projectScope(thisProjectRef).copy(config = Select(DependencyConf))
        if ((dependencyEnable in thisScope get extracted.structure.data) getOrElse true) {
          val name = (sbt.Keys.name in thisScope get extracted.structure.data) getOrElse thisProjectRef.project
          streams.log.info(logPrefix(name) + "Fetch dependencies and align to bundle")
          val result = for {
            appConfiguration <- appConfiguration in thisScope get extracted.structure.data
            ivyLoggingLevel <- ivyLoggingLevel in thisScope get extracted.structure.data
            ivyScala <- ivyScala in thisScope get extracted.structure.data
            pathTarget <- target in thisScope get extracted.structure.data
            updateConfiguration <- updateConfiguration in thisScope get extracted.structure.data
            dependencyEnableCustom <- dependencyEnableCustom in thisScope get extracted.structure.data
            dependencyIgnoreConfiguration <- dependencyIgnoreConfiguration in thisScope get extracted.structure.data
            dependencyPath <- dependencyPath in thisScope get extracted.structure.data
            dependencyResourceFilter <- dependencyResourceFilter in thisScope get extracted.structure.data
            dependencySkipResolved <- dependencySkipResolved in thisScope get extracted.structure.data
            libraryDependenciesCompile <- libraryDependencies in thisScope in Compile get extracted.structure.data
            libraryDependenciesTest <- libraryDependencies in thisScope in Test get extracted.structure.data
          } yield {
            val libraryDependencies = (libraryDependenciesCompile ++ libraryDependenciesTest).distinct
            val argument = TaskArgument(appConfiguration, ivyLoggingLevel, ivySbt, ivyScala, libraryDependencies, name,
              origClassifiersModule, new UpdateConfiguration(updateConfiguration.retrieve, true, ivyLoggingLevel),
              pathBundle, dependencyPath, pathTarget, streams, dependencyEnableCustom, None, true, dependencyClasspath,
              dependencyFilter, dependencyIgnoreConfiguration, dependencyResourceFilter, dependencySkipResolved)
            commonFetchTask(argument, doFetchWithSources)
          }
          result.get
          () // Returns Unit. Return type isn't defined explicitly because it is different for different SBT versions.
        }
      }
  /** Implementation of dependency-bundle-with-artifact */
  def dependencyTaskBundleWithArtifactTask =
    (classifiersModule in updateSbtClassifiers, dependencyBundlePath in DependencyConf,
      dependencyFilter in DependencyConf, dependencyLookupClasspath in DependencyConf,
      ivySbt, packageBin in Compile, state, streams, thisProjectRef) map { (origClassifiersModule, pathBundle,
        dependencyFilter, dependencyClasspath, ivySbt, packageBin, state, streams, thisProjectRef) =>
        val extracted: Extracted = Project.extract(state)
        val thisScope = Load.projectScope(thisProjectRef).copy(config = Select(DependencyConf))
        if ((dependencyEnable in thisScope get extracted.structure.data) getOrElse true) {
          val name = (sbt.Keys.name in thisScope get extracted.structure.data) getOrElse thisProjectRef.project
          streams.log.info(logPrefix(name) + "Fetch dependencies with artifact and align to bundle")
          val result = for {
            appConfiguration <- appConfiguration in thisScope get extracted.structure.data
            ivyLoggingLevel <- ivyLoggingLevel in thisScope get extracted.structure.data
            ivyScala <- ivyScala in thisScope get extracted.structure.data
            pathTarget <- target in thisScope get extracted.structure.data
            updateConfiguration <- updateConfiguration in thisScope get extracted.structure.data
            dependencyEnableCustom <- dependencyEnableCustom in thisScope get extracted.structure.data
            dependencyIgnoreConfiguration <- dependencyIgnoreConfiguration in thisScope get extracted.structure.data
            dependencyPath <- dependencyPath in thisScope get extracted.structure.data
            dependencyResourceFilter <- dependencyResourceFilter in thisScope get extracted.structure.data
            dependencySkipResolved <- dependencySkipResolved in thisScope get extracted.structure.data
            libraryDependenciesCompile <- libraryDependencies in thisScope in Compile get extracted.structure.data
            libraryDependenciesTest <- libraryDependencies in thisScope in Test get extracted.structure.data
          } yield {
            val libraryDependencies = (libraryDependenciesCompile ++ libraryDependenciesTest).distinct
            val argument = TaskArgument(appConfiguration, ivyLoggingLevel, ivySbt, ivyScala, libraryDependencies, name,
              origClassifiersModule, new UpdateConfiguration(updateConfiguration.retrieve, true, ivyLoggingLevel),
              pathBundle, dependencyPath, pathTarget, streams, dependencyEnableCustom, Some(packageBin), true, dependencyClasspath,
              dependencyFilter, dependencyIgnoreConfiguration, dependencyResourceFilter, dependencySkipResolved)
            commonFetchTask(argument, doFetchWithSources)
          }
          result.get
          () // Returns Unit. Return type isn't defined explicitly because it is different for different SBT versions.
        }
      }
  /** Implementation of dependency-fetch-align */
  def dependencyTaskFetchAlignTask =
    (classifiersModule in updateSbtClassifiers, dependencyBundlePath in DependencyConf, dependencyFilter in DependencyConf,
      dependencyLookupClasspath in DependencyConf, ivySbt, state, streams, thisProjectRef) map { (origClassifiersModule, pathBundle,
        dependencyFilter, dependencyClasspath, ivySbt, state, streams, thisProjectRef) =>
        val extracted: Extracted = Project.extract(state)
        val thisScope = Load.projectScope(thisProjectRef).copy(config = Select(DependencyConf))
        if ((dependencyEnable in thisScope get extracted.structure.data) getOrElse true) {
          val name = (sbt.Keys.name in thisScope get extracted.structure.data) getOrElse thisProjectRef.project
          streams.log.info(logPrefix(name) + "Fetch dependencies and align")
          val result = for {
            appConfiguration <- appConfiguration in thisScope get extracted.structure.data
            ivyLoggingLevel <- ivyLoggingLevel in thisScope get extracted.structure.data
            ivyScala <- ivyScala in thisScope get extracted.structure.data
            pathTarget <- target in thisScope get extracted.structure.data
            updateConfiguration <- updateConfiguration in thisScope get extracted.structure.data
            dependencyEnableCustom <- dependencyEnableCustom in thisScope get extracted.structure.data
            dependencyIgnoreConfiguration <- dependencyIgnoreConfiguration in thisScope get extracted.structure.data
            dependencyPath <- dependencyPath in thisScope get extracted.structure.data
            dependencyResourceFilter <- dependencyResourceFilter in thisScope get extracted.structure.data
            dependencySkipResolved <- dependencySkipResolved in thisScope get extracted.structure.data
            libraryDependenciesCompile <- libraryDependencies in thisScope in Compile get extracted.structure.data
            libraryDependenciesTest <- libraryDependencies in thisScope in Test get extracted.structure.data
          } yield {
            val libraryDependencies = (libraryDependenciesCompile ++ libraryDependenciesTest).distinct
            val argument = TaskArgument(appConfiguration, ivyLoggingLevel, ivySbt, ivyScala, libraryDependencies, name,
              origClassifiersModule, new UpdateConfiguration(updateConfiguration.retrieve, true, ivyLoggingLevel),
              pathBundle, dependencyPath, pathTarget, streams, dependencyEnableCustom, None, false, dependencyClasspath,
              dependencyFilter, dependencyIgnoreConfiguration, dependencyResourceFilter, dependencySkipResolved)
            commonFetchTask(argument, doFetchAlign)
          }
          result.get
          () // Returns Unit. Return type isn't defined explicitly because it is different for different SBT versions.
        }
      }
  /** Implementation of dependency-fetch-with-sources */
  def dependencyTaskFetchWithSourcesTask =
    (classifiersModule in updateSbtClassifiers, dependencyBundlePath in DependencyConf, dependencyFilter in DependencyConf,
      dependencyLookupClasspath in DependencyConf, ivySbt, state, streams, thisProjectRef) map { (origClassifiersModule, pathBundle,
        dependencyFilter, dependencyClasspath, ivySbt, state, streams, thisProjectRef) =>
        val extracted: Extracted = Project.extract(state)
        val thisScope = Load.projectScope(thisProjectRef).copy(config = Select(DependencyConf))
        if ((dependencyEnable in thisScope get extracted.structure.data) getOrElse true) {
          val name = (sbt.Keys.name in thisScope get extracted.structure.data) getOrElse thisProjectRef.project
          streams.log.info(logPrefix(name) + "Fetch dependencies with source code")
          val result = for {
            appConfiguration <- appConfiguration in thisScope get extracted.structure.data
            ivyLoggingLevel <- ivyLoggingLevel in thisScope get extracted.structure.data
            ivyScala <- ivyScala in thisScope get extracted.structure.data
            pathTarget <- target in thisScope get extracted.structure.data
            updateConfiguration <- updateConfiguration in thisScope get extracted.structure.data
            dependencyEnableCustom <- dependencyEnableCustom in thisScope get extracted.structure.data
            dependencyIgnoreConfiguration <- dependencyIgnoreConfiguration in thisScope get extracted.structure.data
            dependencyPath <- dependencyPath in thisScope get extracted.structure.data
            dependencyResourceFilter <- dependencyResourceFilter in thisScope get extracted.structure.data
            dependencySkipResolved <- dependencySkipResolved in thisScope get extracted.structure.data
            libraryDependenciesCompile <- libraryDependencies in thisScope in Compile get extracted.structure.data
            libraryDependenciesTest <- libraryDependencies in thisScope in Test get extracted.structure.data
          } yield {
            val libraryDependencies = (libraryDependenciesCompile ++ libraryDependenciesTest).distinct
            val argument = TaskArgument(appConfiguration, ivyLoggingLevel, ivySbt, ivyScala, libraryDependencies, name,
              origClassifiersModule, new UpdateConfiguration(updateConfiguration.retrieve, true, ivyLoggingLevel),
              pathBundle, dependencyPath, pathTarget, streams,
              dependencyEnableCustom, None, false, dependencyClasspath,
              dependencyFilter, dependencyIgnoreConfiguration, dependencyResourceFilter, dependencySkipResolved)
            commonFetchTask(argument, doFetchWithSources)
          }
          result.get
          () // Returns Unit. Return type isn't defined explicitly because it is different for different SBT versions.
        }
      }
  /** Implementation of dependency-fetch */
  def dependencyTaskFetchTask =
    (classifiersModule in updateSbtClassifiers, dependencyBundlePath in DependencyConf, dependencyFilter in DependencyConf,
      dependencyLookupClasspath in DependencyConf, ivySbt, state, streams, thisProjectRef) map {
        (origClassifiersModule, pathBundle, dependencyFilter, dependencyClasspath, ivySbt, state, streams, thisProjectRef) =>
          val extracted: Extracted = Project.extract(state)
          val thisScope = Load.projectScope(thisProjectRef).copy(config = Select(DependencyConf))
          if ((dependencyEnable in thisScope get extracted.structure.data) getOrElse true) {
            val name = (sbt.Keys.name in thisScope get extracted.structure.data) getOrElse thisProjectRef.project
            streams.log.info(logPrefix(name) + "Fetch dependencies")
            val result = for {
              appConfiguration <- appConfiguration in thisScope get extracted.structure.data
              ivyLoggingLevel <- ivyLoggingLevel in thisScope get extracted.structure.data
              ivyScala <- ivyScala in thisScope get extracted.structure.data
              pathTarget <- target in thisScope get extracted.structure.data
              updateConfiguration <- updateConfiguration in thisScope get extracted.structure.data
              dependencyEnableCustom <- dependencyEnableCustom in thisScope get extracted.structure.data
              dependencyIgnoreConfiguration <- dependencyIgnoreConfiguration in thisScope get extracted.structure.data
              dependencyPath <- dependencyPath in thisScope get extracted.structure.data
              dependencyResourceFilter <- dependencyResourceFilter in thisScope get extracted.structure.data
              dependencySkipResolved <- dependencySkipResolved in thisScope get extracted.structure.data
              libraryDependenciesCompile <- libraryDependencies in thisScope in Compile get extracted.structure.data
              libraryDependenciesTest <- libraryDependencies in thisScope in Test get extracted.structure.data
            } yield {
              val libraryDependencies = (libraryDependenciesCompile ++ libraryDependenciesTest).distinct
              val argument = TaskArgument(appConfiguration, ivyLoggingLevel, ivySbt, ivyScala, libraryDependencies, name,
                origClassifiersModule, new UpdateConfiguration(updateConfiguration.retrieve, true, ivyLoggingLevel),
                pathBundle, dependencyPath, pathTarget, streams, dependencyEnableCustom, None, false, dependencyClasspath,
                dependencyFilter, dependencyIgnoreConfiguration, dependencyResourceFilter, dependencySkipResolved)
              commonFetchTask(argument, doFetch)
            }
            result.get
            () // Returns Unit. Return type isn't defined explicitly because it is different for different SBT versions.
          }
      }
  /**
   * Task that returns the union of fullClasspath in Compile and Test configurations
   */
  def dependencyLookupClasspathTask =
    (externalDependencyClasspath in Compile, externalDependencyClasspath in Test) map ((cpA, cpB) => (cpA ++ cpB).distinct)
  /**
   * Dependency resource filter
   * It drops META-INF/ .SF .DSA .RSA files by default
   */
  def resourceFilter(entry: ZipEntry): Boolean =
    Seq("META-INF/.*\\.SF", "META-INF/.*\\.DSA", "META-INF/.*\\.RSA").find(entry.getName().toUpperCase().matches).nonEmpty

  /** Repack sequence of jar artifacts */
  protected def align(arg: TaskArgument, moduleTag: String, code: File, sources: File, targetDirectory: File, resourceFilter: ZipEntry => Boolean, s: TaskStreams,
    alignEntries: HashSet[String] = HashSet[String](), output: JarOutputStream = null): Unit = {
    if (!targetDirectory.exists())
      if (!targetDirectory.mkdirs())
        return s.log.error(logPrefix(arg.name) + "Unable to create " + targetDirectory)
    val target = new File(targetDirectory, code.getName)
    if (output == null) {
      s.log.info(logPrefix(arg.name) + "Fetch and align " + moduleTag)
      s.log.debug(logPrefix(arg.name) + "Save result to " + target.getAbsoluteFile())
    } else
      s.log.info(logPrefix(arg.name) + "Fetch and align " + moduleTag + ", target: bundle")
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
              case e: Throwable =>
            }
            return s.log.error(logPrefix(arg.name) + "Unable to delete " + target)
          }
        jarTarget = try {
          new JarOutputStream(new BufferedOutputStream(new FileOutputStream(target, true)), jarCode.getManifest())
        } catch {
          case e: NullPointerException =>
            s.log.warn(logPrefix(arg.name) + code + " has broken manifest")
            new JarOutputStream(new BufferedOutputStream(new FileOutputStream(target, true)))
        }
      }
      // copy across all entries from the original code jar
      copy(arg, alignEntries, jarCode, jarTarget, resourceFilter, s)
      // copy across all entries from the original sources jar
      copy(arg, alignEntries, jarSources, jarTarget, resourceFilter, s)
    } catch {
      case e: Throwable =>
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
  protected def commonFetchTask(arg: TaskArgument, userFunction: (TaskArgument, Seq[(sbt.ModuleID, File)], Seq[(sbt.ModuleID, File)]) => Unit): UpdateReport =
    synchronized {
      Classpaths.withExcludes(arg.pathTarget, arg.origClassifiersModule.classifiers, Defaults.lock(arg.appConfiguration)) { excludes =>
        import arg.origClassifiersModule.{ id => origClassifiersModuleID, modules => origClassifiersModuleDeps }
        if (arg.dependencyBundle)
          arg.streams.log.info(logPrefix(arg.name) + "Create bundle " + arg.pathBundle)
        // do default update-sbt-classifiers with libDeps
        val libDeps = arg.dependencyClasspath.flatMap(_.get(moduleID.key))
        val extClassifiersModuleDeps = {
          val all = arg.dependencyFilter match {
            case Some(filter) => (origClassifiersModuleDeps ++ libDeps).filter(filter)
            case None => (origClassifiersModuleDeps ++ libDeps)
          }
          if (arg.dependencyIgnoreConfiguration)
            all.map(_.copy(configurations = None))
          else
            all
        }
        // skip dependency that already have explicit artifacts which points to local resources
        val extClassifiersModuleDepsFiltered = {
          if (arg.dependencySkipResolved)
            extClassifiersModuleDeps.filterNot(moduleId =>
              arg.libraryDependencies.exists(id =>
                id.name == moduleId.name && id.organization == moduleId.organization && id.revision == moduleId.revision &&
                  id.explicitArtifacts.nonEmpty && id.explicitArtifacts.forall(_.url.map(_.getProtocol()) == Some("file"))))
          else
            extClassifiersModuleDeps
        }
        val customConfig = GetClassifiersConfiguration(arg.origClassifiersModule, excludes, arg.updateConfiguration, arg.ivyScala)
        val customBaseModuleID = restrictedCopy(origClassifiersModuleID, true).copy(name = origClassifiersModuleID.name + "$sbt")
        val customIvySbtModule = new arg.ivySbt.Module(InlineConfiguration(customBaseModuleID, ModuleInfo(customBaseModuleID.name), extClassifiersModuleDepsFiltered).copy(ivyScala = arg.ivyScala))
        val customUpdateReport = IvyActions.update(customIvySbtModule, arg.updateConfiguration, arg.streams.log)
        val newConfig = customConfig.copy(module = arg.origClassifiersModule.copy(modules = customUpdateReport.allModules))
        val updateReport = IvyActions.updateClassifiers(arg.ivySbt, newConfig, arg.streams.log)
        // process updateReport
        // get all sources
        val (sources, other) = updateReport.toSeq.partition {
          case (_, _, Artifact(_, _, _, Some(Artifact.SourceClassifier), _, _, _), _) => true
          case _ => false
        }
        val sourceObjects = sources.map { case (configuration, moduleId, artifact, file) => (moduleId, file) }
        val codeObjects = other.map {
          case (configuration, moduleId, artifact, file) if artifact.classifier == None || artifact.classifier == Some("") =>
            Some((moduleId, file))
          case _ =>
            None
        }.flatten
        // process all jars
        other.sortBy(_._2.toString).foreach { module => arg.streams.log.debug("add " + module._2) }
        userFunction(arg, sourceObjects, codeObjects)
        // add unprocessed modules
        if (arg.dependencyEnableCustom) {
          // get all unprocessed dependencies with ModuleID
          val unprocessedUnfiltered = arg.dependencyFilter match {
            case Some(filter) =>
              extClassifiersModuleDeps.filterNot(other.map(_._2).contains).distinct.filter(filter)
            case None =>
              extClassifiersModuleDeps.filterNot(other.map(_._2).contains).distinct
          }
          unprocessedUnfiltered.sortBy(_.toString).foreach { module => arg.streams.log.debug("add unprocessed " + module) }
          // get all unprocessed dependencies or dependencies without ModuleID
          val unprocessed = arg.dependencyClasspath.sortBy(_.toString).map(classpath => classpath.get(moduleID.key) match {
            case Some(moduleId) =>
              if (unprocessedUnfiltered.contains(moduleId)) {
                // lookup for original ModuleIDs with explicit artifacts that points to local file system
                val originalModuleID = arg.libraryDependencies.find(id =>
                  id.name == moduleId.name && id.organization == moduleId.organization && id.revision == moduleId.revision &&
                    id.explicitArtifacts.nonEmpty && id.explicitArtifacts.forall(_.url.map(_.getProtocol()) == Some("file")))
                Some(originalModuleID getOrElse moduleId.name % moduleId.organization % moduleId.revision from classpath.data.toURI().toURL().toString)
              } else
                None // already processed
            case None =>
              Some("UNKNOWN" % "UNKNOWN" % "UNKNOWN" from classpath.data.toURI().toURL().toString)
          }).flatten
          if (arg.dependencyBundle)
            unprocessed.foreach {
              moduleId =>
                val codeArtifact = moduleId.explicitArtifacts.find(_.classifier == None)
                val sourceCodeArtifact = moduleId.explicitArtifacts.find(_.classifier == Some(Artifact.SourceClassifier))
                (codeArtifact, sourceCodeArtifact) match {
                  case (Some(Artifact(_, _, _, _, _, Some(codeURL), _)), Some(Artifact(_, _, _, _, _, Some(sourceCodeURL), _))) =>
                    val code = new File(codeURL.toURI)
                    val source = new File(sourceCodeURL.toURI)
                    userFunction(arg, Seq((moduleId, source)), Seq((moduleId, code)))
                  case (Some(Artifact(_, _, _, _, _, Some(codeURL), _)), _) =>
                    val code = new File(codeURL.toURI)
                    arg.streams.log.info(logPrefix(arg.name) + "Fetch custom library " + code.getName())
                    copyToCodeBundle(arg, code)
                    copyToSourceBundle(arg, code)
                  case _ =>
                    arg.streams.log.error(logPrefix(arg.name) + "Unable to aquire artifacts for module " + moduleId)
                }
            }
          else
            unprocessed.foreach {
              moduleId =>
                val codeArtifact = moduleId.explicitArtifacts.find(_.classifier == None)
                val sourceCodeArtifact = moduleId.explicitArtifacts.find(_.classifier == Some(Artifact.SourceClassifier))
                (codeArtifact, sourceCodeArtifact) match {
                  case (Some(Artifact(_, _, _, _, _, Some(codeURL), _)), Some(Artifact(_, _, _, _, _, Some(sourceCodeURL), _))) =>
                    val code = new File(codeURL.toURI)
                    val source = new File(sourceCodeURL.toURI)
                    userFunction(arg, Seq((moduleId, source)), Seq((moduleId, code)))
                  case (Some(Artifact(_, _, _, _, _, Some(codeURL), _)), _) =>
                    val code = new File(codeURL.toURI)
                    arg.streams.log.info(logPrefix(arg.name) + "Fetch custom library " + code.getName())
                    sbt.IO.copyFile(code, new File(arg.pathDependency, code.getName()), false)
                  case _ =>
                    arg.streams.log.error(logPrefix(arg.name) + "Unable to aquire artifacts for module " + moduleId)
                }
            }
        }
        if (arg.dependencyBundle) {
          // add artifact
          arg.dependencyArtifact.foreach(copyToCodeBundle(arg, _))
          arg.dependencyArtifact.foreach(copyToSourceBundle(arg, _))
          arg.bundleJarCode.flush()
          arg.bundleJarCode.close()
          arg.bundleJarSource.flush()
          arg.bundleJarSource.close()
          // create bundle description
          val directory = arg.pathBundle.getParentFile()
          val file = arg.pathBundle.getName() + ".description"
          val descriptionFile = new File(directory, file)
          Some(new PrintWriter(descriptionFile)).foreach { writer =>
            try {
              writer.write(arg.bundleResources.toList.sorted.mkString("\n"))
            } catch {
              case e: Throwable =>
                arg.streams.log.error(logPrefix(arg.name) + "Unable to create bundle description " + descriptionFile.getAbsolutePath() + " " + e)
            } finally {
              try { writer.close } catch { case e: Throwable => }
            }
          }
        }
        updateReport
      }
    }
  /** Specific part for tasks dependency-fetch-align, dependency-bundle, dependency-bundle-with-artifact */
  protected def doFetchAlign(arg: TaskArgument, sourceObjects: Seq[(sbt.ModuleID, File)],
    codeObjects: Seq[(sbt.ModuleID, File)]): Unit = codeObjects.foreach {
    case (module, codeJar) =>
      sourceObjects.find(source => source._1 == module) match {
        case Some((_, sourceJar)) =>
          if (arg.dependencyBundle) {
            align(arg, module.toString, codeJar, sourceJar, arg.pathDependency, resourceFilter, arg.streams, arg.bundleEntries, arg.bundleJarCode)
            arg.bundleResources += codeJar.getAbsolutePath()
          } else
            align(arg, module.toString, codeJar, sourceJar, arg.pathDependency, resourceFilter, arg.streams)
        case None =>
          arg.streams.log.debug(logPrefix(arg.name) + "Skip align for dependency " + module + " - sources not found ")
          if (arg.dependencyBundle) {
            arg.streams.log.info(logPrefix(arg.name) + "Fetch " + module + " to bundle without source code")
            copyToCodeBundle(arg, codeJar)
          } else {
            arg.streams.log.info(logPrefix(arg.name) + "Fetch " + module + " without source code")
            val codeTarget = new File(arg.pathDependency, codeJar.getName())
            arg.streams.log.debug(logPrefix(arg.name) + "Save result to " + codeTarget.getAbsolutePath())
            sbt.IO.copyFile(codeJar, codeTarget, false)
          }
      }
  }
  /** Specific part for task dependency-fetch-with-sources */
  protected def doFetchWithSources(arg: TaskArgument, sourceObjects: Seq[(sbt.ModuleID, File)],
    codeObjects: Seq[(sbt.ModuleID, File)]): Unit = codeObjects.foreach {
    case (module, codeJar) =>
      sourceObjects.find(source => source._1 == module) match {
        case Some((_, sourceJar)) =>
          if (arg.dependencyBundle) {
            arg.streams.log.info(logPrefix(arg.name) + "Fetch with source code " + module + ", target: bundle")
            copyToCodeBundle(arg, codeJar)
            copyToSourceBundle(arg, sourceJar)
            arg.bundleResources += codeJar.getAbsolutePath()
          } else {
            val codeTarget = new File(arg.pathDependency, codeJar.getName())
            val sourceTarget = new File(arg.pathDependency, sourceJar.getName())
            arg.streams.log.info(logPrefix(arg.name) + "Fetch with source code " + module)
            arg.streams.log.debug(logPrefix(arg.name) + "Save results to " + codeTarget.getParentFile.getAbsolutePath())
            sbt.IO.copyFile(codeJar, codeTarget, false)
            sbt.IO.copyFile(sourceJar, sourceTarget, false)
          }
        case None =>
          if (arg.dependencyBundle) {
            arg.streams.log.info(logPrefix(arg.name) + "Fetch with source code " + module + ", target: bundle")
            copyToCodeBundle(arg, codeJar)
          } else {
            arg.streams.log.info(logPrefix(arg.name) + "Fetch with source code " + module)
            val codeTarget = new File(arg.pathDependency, codeJar.getName())
            arg.streams.log.debug(logPrefix(arg.name) + "Save results to " + codeTarget.getParentFile.getAbsolutePath())
            sbt.IO.copyFile(codeJar, codeTarget, false)
          }
      }
  }
  /** Specific part for task dependency-fetch */
  protected def doFetch(arg: TaskArgument, sourceObjects: Seq[(sbt.ModuleID, File)],
    codeObjects: Seq[(sbt.ModuleID, File)]): Unit = codeObjects.foreach {
    case (module, codeJar) =>
      sourceObjects.find(source => source._1 == module) match {
        case Some((_, sourceJar)) =>
          arg.streams.log.info(logPrefix(arg.name) + "Fetch " + module)
          val codeTarget = new File(arg.pathDependency, codeJar.getName())
          arg.streams.log.debug(logPrefix(arg.name) + "Save result to " + codeTarget.getAbsolutePath())
          sbt.IO.copyFile(codeJar, codeTarget, false)
        case None =>
          arg.streams.log.debug(logPrefix(arg.name) + "Skip " + module)
      }
  }

  /** Repack content of jar artifact */
  private def alignScalaSource(arg: TaskArgument, alignEntries: HashSet[String], entry: ZipEntry, content: String, s: TaskStreams): Option[ZipEntry] = {
    val searchFor = "/" + entry.getName.takeWhile(_ != '.')
    val distance = alignEntries.toSeq.map(path => (path.indexOf(searchFor), path)).filter(_._1 > 1).sortBy(_._1).headOption
    distance match {
      case Some((idx, entryPath)) =>
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
      case None =>
        var path = Seq[String]()
        val pattern = """\s*package\s+([a-z\\._$-]+).*""".r
        content.split("\n").foreach {
          case pattern(packageName) =>
            path = path :+ packageName.replaceAll("\\.", "/")
          case line =>
        }
        if (path.nonEmpty) {
          val prefix = path.mkString("/") + "/"
          alignEntries.toSeq.find(_.startsWith(prefix)) match {
            case Some(path) =>
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
            case None =>
              s.log.warn(logPrefix(arg.name) + "Failed to align source " + entry.getName())
              None
          }
        } else
          None
    }
  }
  /** Copy content of jar artifact */
  private def copy(arg: TaskArgument, alignEntries: HashSet[String], in: JarInputStream, out: JarOutputStream, resourceFilter: ZipEntry => Boolean, s: TaskStreams) {
    var entry: ZipEntry = null
    // copy across all entries from the original code jar
    var value: Int = 0
    try {
      val buffer = new Array[Byte](2048)
      entry = in.getNextEntry()
      while (entry != null) {
        if (alignEntries(entry.getName))
          s.log.debug(logPrefix(arg.name) + "Skip, entry already in jar: " + entry.getName())
        else if (resourceFilter(entry)) {
          s.log.debug(logPrefix(arg.name) + "Skip, filtered " + entry)
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
                entry =>
                  if (alignEntries(entry.getName))
                    s.log.debug(logPrefix(arg.name) + "Skip, entry already in jar: " + entry.getName())
                  else {
                    out.putNextEntry(entry)
                    out.write(bos.toByteArray())
                  }
              }
          } catch {
            case e: ZipException =>
              s.log.error(logPrefix(arg.name) + "Zip failed: " + e.getMessage())
          }
        entry = in.getNextEntry()
      }
    } catch {
      case e: Throwable =>
        s.log.error(logPrefix(arg.name) + "Copy failed: " + e.getClass().getName() + " " + e.getMessage())
    }
  }
  /** Copy content to code bundle */
  private def copyToCodeBundle(arg: TaskArgument, codeJar: File) {
    arg.streams.log.debug(logPrefix(arg.name) + "Append %s to code bundle".format(codeJar.getName()))
    // copy across all entries from the original code jar
    val jarCode = new JarInputStream(new FileInputStream(codeJar))
    try {
      copy(arg, arg.bundleEntries, jarCode, arg.bundleJarCode, resourceFilter, arg.streams)
      arg.bundleResources += codeJar.getAbsolutePath()
    } catch {
      case e: Throwable =>
        arg.streams.log.error(logPrefix(arg.name) + "Unable to merge: " + e.getClass().getName() + " " + e.getMessage())
    } finally {
      if (jarCode != null)
        jarCode.close()
    }
  }
  /** Copy content to source bundle */
  private def copyToSourceBundle(arg: TaskArgument, sourceJar: File) {
    arg.streams.log.debug("append %s to source bundle".format(sourceJar.getName()))
    // copy across all entries from the original code jar
    val jarSource = new JarInputStream(new FileInputStream(sourceJar))
    try {
      copy(arg, arg.bundleEntries, jarSource, arg.bundleJarSource, resourceFilter, arg.streams)
    } catch {
      case e: Throwable =>
        arg.streams.log.error(logPrefix(arg.name) + "Unable to merge: " + e.getClass().getName() + " " + e.getMessage())
    } finally {
      if (jarSource != null)
        jarSource.close()
    }
  }
  private[this] def restrictedCopy(m: ModuleID, confs: Boolean) =
    ModuleID(m.organization, m.name, m.revision, crossVersion = m.crossVersion, extraAttributes = m.extraAttributes, configurations = if (confs) m.configurations else None)

  /** Consolidated argument with all required information */
  case class TaskArgument(
    /** Application configuration that provides information about SBT process */
    appConfiguration: AppConfiguration,
    /** The property representing Ivy process log level */
    ivyLogLevel: UpdateLogging.Value,
    /** Ivy wrapper that contains org.apache.ivy.Ivy and org.apache.ivy.core.settings.IvySettings */
    ivySbt: IvySbt,
    /** Ivy scala artifacts description */
    ivyScala: Option[IvyScala],
    /** Original ModuleIDs from SBT project definition */
    libraryDependencies: Seq[ModuleID],
    /** Current project name */
    name: String,
    /** GetClassifiersModule */
    origClassifiersModule: GetClassifiersModule,
    /** Update configuration */
    updateConfiguration: UpdateConfiguration,
    /** Bundle path with jar name */
    pathBundle: java.io.File,
    /** Path to Fetched artifacts */
    pathDependency: java.io.File,
    /** Target path */
    pathTarget: java.io.File,
    /** SBT task streams for logging */
    streams: TaskStreams,
    /** Flag indicating whether custom libraries without ModuleID should be fetched */
    dependencyEnableCustom: Boolean,
    /** The property representing artifact location */
    dependencyArtifact: Option[java.io.File],
    /** Flag indicating whether plugin should create bundle */
    dependencyBundle: Boolean,
    /** Classpath that is used to build dependency sequence */
    dependencyClasspath: Classpath,
    /** Fetch filter */
    dependencyFilter: Option[ModuleFilter],
    /** Flag indicating whether plugin should ignore a dependency configuration while lookup ('test' for example) */
    dependencyIgnoreConfiguration: Boolean,
    /** Function that filters jar content */
    dependencyResourceFilter: ZipEntry => Boolean,
    /** Skip resolved dependencies with explicit artifacts which points to local resources */
    dependencySkipResolved: Boolean) {
    /** Output stream for bundle with compiled code */
    val bundleJarCode: JarOutputStream = if (dependencyBundle) {
      assert(pathBundle.name endsWith ".jar", "incorrect dependency-bundle-path, must be path to jar file")
      pathBundle.delete() // remove old bundle
      new JarOutputStream(new BufferedOutputStream(new FileOutputStream(pathBundle, true)))
    } else
      null
    /** Output stream for bundle with compiled code */
    val bundleJarSource: JarOutputStream = if (dependencyBundle) {
      assert(pathBundle.name endsWith ".jar", "incorrect dependency-bundle-path, must be path to jar file")
      val directory = pathBundle.getParentFile()
      val name = pathBundle.getName
      val pathSourceBundle = new File(directory, name.replaceFirst(""".jar$""", """-sources.jar"""))
      pathSourceBundle.delete() // remove old bundle
      new JarOutputStream(new BufferedOutputStream(new FileOutputStream(pathSourceBundle, true)))
    } else
      null
    val bundleEntries = HashSet[String]()
    val bundleResources = HashSet[String]()
  }
}
