sbt-dependency-manager [![Build Status](https://travis-ci.org/digimead/sbt-dependency-manager.png?branch=master)](https://travis-ci.org/digimead/sbt-dependency-manager)
======================

Short introduction: [Simple-build-tool plugin with Eclipse in 5 Minutes](http://youtu.be/3K8knvkVAyc) on Youtube (demo of one of the first versions) or [look at the test project](https://github.com/digimead/sbt-dependency-manager/tree/master/src/sbt-test/dependency-manager/simple). Please, open `test` file.

What is it? You may fetch [SBT](https://github.com/sbt/sbt "Simple Build Tool") project artifacts, compose jars with source code, align sources inside jars for your favorite IDE

It is provide an ability:
* to fetch __all dependency jars (include sbt-dependency-manager itself)__ to target folder
* to fetch __all dependency jars with sources (include sbt-dependency-manager itself)__ to target folder
* to fetch __all dependency jars with sources, merge them (include sbt-dependency-manager itself)__ and save to target folder
* join all fetched artifacts to solid jar bundle, that simplify project setup and provide rapid develoment for 3rd party. Especially when you have tons of dependencies from different sources: mix of local artifacts, OSGi bundles from P2 update sites and ivy/maven libraries :-)

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

You may find sample project at [src/sbt-test/dependency-manager/simple](https://github.com/digimead/sbt-dependency-manager/tree/master/src/sbt-test/dependency-manager/simple)

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
      lazy val dm = uri("git://github.com/digimead/sbt-dependency-manager.git#0.6.4.2")
    }
```

You may find more information about Build.scala at [https://github.com/harrah/xsbt/wiki/Plugins](https://github.com/harrah/xsbt/wiki/Plugins)

### As published jar artifact

Supported SBT versions: 0.11.3, 0.12.3, 0.13.0-20130520-052156. Add to your _project/plugins.sbt_

    addSbtPlugin("org.digimead" % "sbt-dependency-manager" % "0.6.4.2")

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
    dependencyPath <<= (baseDirectory) (_ / "my-aling-dir")
```

or

``` scala
    DMKey.dependencyPath in DMConf <<= (baseDirectory) (_ / "deps")
```

### Fetch all dependencies

By default sbt-dependency-manager skips "org.scala-lang" and "org.scala-sbt". If you need all dependencies do

``` scala
    dependencyFilter := None
```

### Filter dependencies

For example I want to fetch all for one of my plugin all project dependecies and resources(jar + source code) of all artifacts with organizations org.scala-sbt, org.digimead, com.typesafe.sbt.

``` scala
    // common dependency filter
    DMKey.dependencyFilter in DMConf <<= DMKey.dependencyFilter in DMConf map (df => df.map(df =>
      // plus "org.scala-sbt" modules
      moduleFilter(organization = GlobFilter("org.scala-sbt")) |
        // plus project plugin modules
        moduleFilter(organization = GlobFilter("org.digimead")) |
        moduleFilter(organization = GlobFilter("com.typesafe.sbt")))))
```

For more about module filters look at [SBT Wiki](http://www.scala-sbt.org/release/docs/Detailed-Topics/Update-Report#filter-basics)

### Jar entities filter

You may filter jar artifacts from populated resources. For example default filter is

    dependencyResourceFilter := resourceFilter
    def resourceFilter(entry: ZipEntry): Boolean =
      Seq("META-INF/.*\\.SF", "META-INF/.*\\.DSA", "META-INF/.*\\.RSA").find(entry.getName().toUpperCase().matches).nonEmpty

So we are dropped all 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA' which absolutely unneeded while developing.

### Align project dependencies

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

You may inspect all available parameters in [Keys object](https://github.com/digimead/sbt-dependency-manager/tree/master/src/main/scala/sbt/dependency/manager/Keys.scala).

### Options ###

* __add-custom__ (dependencyAddCustom) - Add custom(unknown) libraries to results.
* __bundle-path__ (dependencyBundlePath) - Path to bundle with fetched artifacts.
* __enable__ (dependencyEnable) - Enable/disable plugin. It is very usefull in nested project environments when parent project already provided all dependencies and child project inherits parent settings.
* __enable-custom-libraries__ (dependencyEnableCustom) - Enables to fetch libraries that come from alternative sources like unmanaged artifacts or plugin specific library.
* __filter__ (dependencyFilter) - Processing dependencies only with particular sbt.ModuleID. [Example](#filter-dependencies).
* __ignore-configurations__ (dependencyIgnoreConfigurations) - Ignore configurations while lookup, 'test' for example.
* __lookup-classpath__ (dependencyLookupClasspath) - Classpath that is used for building the sequence of fetched dependencies.
* __path__ (dependencyPath) - Target directory for dependency jars. [Example](#usage).
* __resource-filter__ (dependencyResourceFilter) - Fuction for filtering jar content when we use `dependency-fetch-align`. [Example](#jar-entities-filter).
* __skip-resolved__ (dependencySkipResolved) - Skip already resolved dependencies with explicit artifacts which points to local resources. For example dependencies which you add manually to your SBT project like `"a" % "b" % "c" from file://bla/bla/...`

### Tasks ###

* __dependency-bundle__ - Fetch dependency code and source jars. Save results to bundle.
* __dependency-bundle-with-artifact__ - Fetch dependency code and source jars, add project artefact. Save results to bundle.
* __dependency-fetch__ - Fetch project jars. Save result to target directory.
* __dependency-fetch-align__ - Fetch project jars, merge them with source code. Save result to target directory.
* __dependency-fetch-with-sources__ - Fetch project jars, fetch source jars. Save result to target directory.

### Other ###

* __predefined-classpath-filter__ - Predefined filter that accepts all modules in project classpath. It consists of a sequence of sbt.ModuleFilter that generated from project dependencies and grouped by logical OR.


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
