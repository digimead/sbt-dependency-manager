sbt-dependency-manager [![Build Status](https://travis-ci.org/digimead/sbt-dependency-manager.png?branch=master)](https://travis-ci.org/sbt-android-mill/sbt-dependency-manager)
======================

Short introduction: [Simple-build-tool plugin with Eclipse in 5 Minutes](http://youtu.be/3K8knvkVAyc) on Youtube (demo of one of the first versions) or [look at the test project](https://github.com/sbt-android-mill/sbt-dependency-manager/tree/master/src/sbt-test/dependency-manager/simple). Please, open `test` file

What is it? You may fetch [SBT](https://github.com/sbt/sbt "Simple Build Tool") project artifacts, compose jars with source code, align sources inside jars for your favorite IDE

It is provide an ability:
* to fetch __all dependency jars (include sbt-dependency-manager itself)__ to target folder
* to fetch __all dependency jars with sources (include sbt-dependency-manager itself)__ to target folder
* to fetch __all dependency jars with sources, merge them (include sbt-dependency-manager itself)__ and save to target folder

If you want to improve it, please send mail to sbt-android-mill at digimead.org. You will be added to the group. Please, feel free to add yourself to authors.

SBT source code is really simple to read and simple to extend :-)

This readme cover all plugin functionality, even if it is written in broken english (would you have preferred well written russian :-) Please, correct it, if you find something inappropriate.

Table of contents
-----------------

- [Adding to your project](#adding-to-your-project)
    - [Via interactive build](#via-interactive-build)
    - [As published jar artifact](#as-published-jar-artifact)
    - [As local build](#as-local-build)
    - [Activate in your project](#activate-in-your-project)
- [Usage](#usage)
    - [Fetch all dependencies](#fetch-all-dependencies)
    - [Filter dependencies](#filter-dependencies)
    - [Align project dependencies](#align-project-dependencies)
- [Internals](#internals)
    - [Options](#options)
    - [Tasks](#tasks)
- [Demonstration](#demonstration)
- [FAQ](#faq)
- [Authors](#authors)
- [License](#license)
- [Copyright](#copyright)

## Adding to your project

You may find sample project at [src/sbt-test/dependency-manager/simple](https://github.com/sbt-android-mill/sbt-dependency-manager/tree/master/src/sbt-test/dependency-manager/simple)

### Via interactive build

Supported SBT versions: 0.11.x, 0.12.x, 0.13.x-SNAPSHOT.

Create a

 * _project/plugins/project/Build.scala_ - for older simple-build-tool
 * _project/project/Build.scala_ - for newer simple-build-tool

file that looks like the following:

``` scala
    import sbt._
    object PluginDef extends Build {
      override def projects = Seq(root)
      lazy val root = Project("plugins", file(".")) dependsOn(dm)
      lazy val dm = uri("git://github.com/sbt-android-mill/sbt-dependency-manager.git#0.6.1")
    }
```

You may find more information about Build.scala at [https://github.com/harrah/xsbt/wiki/Plugins](https://github.com/harrah/xsbt/wiki/Plugins)

### As published jar artifact

Supported SBT versions: 0.11.3, 0.12.3, 0.13.0-20130520-052156

    "org.digimead" % "sbt-dependency-manager" % "0.6.4"

Maven repository:

    resolvers += "digimead-maven" at "http://storage.googleapis.com/maven.repository.digimead.org/"

Ivy repository:

    resolvers += Resolver.url("digimead-ivy", url("http://storage.googleapis.com/ivy.repository.digimead.org/"))(Resolver.defaultIvyPatterns)

### As local build

Clone this repository to your development system then do `sbt publish-local`

### Activate in your project

For _build.sbt_, simply add:

``` scala
    import sbt.dependency.manager._
    
    activateDependencyManager
```

For _Build.scala_:

``` scala
    import sbt.dependency.manager._
    
    ... yourProjectSettings ++ activateDependencyManager
```

## Usage ##

By default aligned jars saved to _target/deps_ Change _dependenciesPath_ at your project to something like

``` scala
    dependenciesPath <<= (target in LocalRootProject) map { _ / "my-align-dir" }
```

or

``` scala
    dependenciesPath <<= (baseDirectory) (_ / "my-aling-dir")
```

### Fetch all dependencies

By default sbt-dependency-manager skip "org.scala-lang" and "org.scala-sbt". If you need all dependencies do

``` scala
    dependencyFilter := Seq()
```

### Filter dependencies

``` scala
    dependencyFilter <<= (dependencyClasspathNarrow) map { cp =>
      val filter1 = moduleFilter(organization = "org.scala-lang")
      val filter2 = moduleFilter(organization = "org.other")
      val filter3 = moduleFilter(organization = "com.blabla")
      Some(cp.flatMap(_.get(moduleID.key)).filterNot(filter1).filterNot(filter2).filterNot(filter3))
    },
````

More about module filters look at [SBT Wiki](https://github.com/harrah/xsbt/wiki/Update-Report)

### Align project dependencies ###

1. Download all project __and SBT__ dependencies, sources, javadocs

2. Merge code jars with sources

3. Align sources inside jars

SBT task name

```
> dependency-fetch-align
```

It is very useful to develop simple-build-tool plugins. Most SBT source code are unaligned. Original sources saved in root directory of jar, but it binded to different packages. This situation prevent source code lookup in most common situations. This is very annoying. SBT _*-sources.jar_ was mostly useless in development before sbt-dependenc-manager ;-)

### Fetch project dependencies with sources to '123' directory

```
> set dependencyPath <<= baseDirectory map {(f) => f / "123" }
> dependency-fetch-with-sources
```
 
Internals
---------

### Options ###

* __dependency-path__ (dependencyPath) - Target directory for dependency jars
* __dependency-filter__ (dependencyFilter) - Processing dependencies only with particular sbt.ModuleID
* __dependency-add-custom__ (dependencyAddCustom) - Add custom(unknown) libraries to results
* __dependency-classpath-narrow__ (dependencyClasspathNarrow) - Union of dependencyClasspath from Compile and Test configurations
* __dependency-classpath-wide__ (dependencyClasspathWide) - Union of fullClasspath from Compile and Test configurations
* __dependency-ignore-configurations__ (dependencyIgnoreConfigurations) - Ignore configurations while lookup, 'test' for example

### Tasks ###

* __dependency-fetch__ - Fetch project jars. Save result to target directory
* __dependency-fetch-align__ - Fetch project jars, merge them with source code. Save result to target directory
* __dependency-fetch-with-sources__ - Fetch project jars, fetch source jars. Save result to target directory

Demonstration
-------------

[Simple-build-tool plugin with Eclipse in 5 Minutes](http://youtu.be/3K8knvkVAyc) on Youtube

HD quality [Simple-build-tool plugin with Eclipse in 5 Minutes](https://github.com/downloads/sbt-android-mill/sbt-android-mill-extra/EclipseSBT.mp4) - 60,5Mb

Developing simple SBT plugin in Eclipse IDE with

* autocomplete
* __sources lookup__
* __debug SBT tasks in Eclipse debugger__ (I asked about debugging in SBT mailing list, but no one can answer. I suspect that others people for debug sbt plugins used print or s.log.debug. lol ;-) )
* implicit lookup
* types lookup
* refactoring support

... and bunch of other standard features

_PS sbt-dependency-manager obsoletes capabilities provided by sbt deliver-local + IvyDE or sbteclipse plugin_

FAQ
---

Authors
-------

* Alexey Aksenov

License
-------

The sbt-dependency-manager is licensed to you under the terms of
the Apache License, version 2.0, a copy of which has been
included in the LICENSE file.

Copyright
---------

Copyright © 2012-2013 Alexey B. Aksenov/Ezh. All rights reserved.
