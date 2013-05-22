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

// PRODUCTION CONFIGURATION

ScriptedPlugin.scriptedSettings

name := "sbt-dependency-manager"

organization := "org.digimead"

version <<= (baseDirectory) { (b) => scala.io.Source.fromFile(b / "version").mkString.trim }

scalacOptions ++= Seq("-unchecked", "-deprecation", "-Xcheckinit", "-Xfatal-warnings")

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")

sbtPlugin := true

scriptedBufferLog := false

scriptedLaunchOpts := Seq("-Xms512m", "-Xmx512m", "-XX:MaxPermSize=256m")

//logLevel := Level.Debug
