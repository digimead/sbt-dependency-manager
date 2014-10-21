//
// Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

ScriptedPlugin.scriptedSettings

name := "sbt-dependency-manager"

description := "SBT plugin that fetches project artifacts, composes jars with source code and aligns sources inside jars for your favorite IDE"

licenses := Seq("The Apache Software License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

organization := "org.digimead"

organizationHomepage := Some(url("http://digimead.org"))

homepage := Some(url("https://github.com/digimead/sbt-dependency-manager"))

version <<= (baseDirectory) { (b) => scala.io.Source.fromFile(b / "version").mkString.trim }

// There is no "-Xfatal-warnings" because we have cross compilation against different Scala versions
scalacOptions ++= Seq("-encoding", "UTF-8", "-unchecked", "-deprecation", "-Xcheckinit")

javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")

sbtPlugin := true

scriptedBufferLog := false

resourceGenerators in Compile <+=
  (resourceManaged in Compile, name, version) map { (dir, n, v) =>
    val file = dir / "version-%s.properties".format(n)
    val contents = "name=%s\nversion=%s\nbuild=%s\n".format(n, v, ((System.currentTimeMillis / 1000).toInt).toString)
    IO.write(file, contents)
    Seq(file)
  }

resolvers ++= Seq(
  "dependency-mananger-digimead-maven" at "http://storage.googleapis.com/maven.repository.digimead.org/",
  Resolver.url("dependency-mananger-typesafe-ivy-releases-for-online-crossbuild", url("http://repo.typesafe.com/typesafe/ivy-releases/"))(Resolver.defaultIvyPatterns),
  Resolver.url("dependency-mananger-typesafe-ivy-snapshots-for-online-crossbuild", url("http://repo.typesafe.com/typesafe/ivy-snapshots/"))(Resolver.defaultIvyPatterns),
  Resolver.url("dependency-mananger-typesafe-repository-for-online-crossbuild", url("http://typesafe.artifactoryonline.com/typesafe/ivy-releases/"))(Resolver.defaultIvyPatterns),
  Resolver.url("dependency-mananger-typesafe-shapshots-for-online-crossbuild", url("http://typesafe.artifactoryonline.com/typesafe/ivy-snapshots/"))(Resolver.defaultIvyPatterns))

//logLevel := Level.Debug
