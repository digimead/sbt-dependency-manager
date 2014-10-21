SBT Dependency Manager [![Build Status](https://travis-ci.org/digimead/sbt-dependency-manager.png?branch=master)](https://travis-ci.org/digimead/sbt-dependency-manager)
======================

There is a short introduction on Youtube: [Simple-build-tool plugin with Eclipse in 5 Minutes](http://youtu.be/3K8knvkVAyc). It was one of the ealist versions. <br/>
There is a [sample project][sp]. Please, overview `test` file which contains interactive example in [Scripted format][sc].

What is it? You may fetch [SBT](https://github.com/sbt/sbt "Simple Build Tool") project artifacts, compose jars with source code, *align* sources inside jars for your favorite IDE

It provides an ability:

* to fetch __all dependency jars (include sbt-dependency-manager itself)__ to target folder
* to fetch __all dependency jars with sources (include sbt-dependency-manager itself)__ to target folder
* to fetch __all dependency jars with sources, merge them (include sbt-dependency-manager itself)__ and save to target folder
* join all fetched artifacts to solid consolidated jar, that simplify project setup and provide rapid develoment dependency for 3rd party. Especially when you have tons of dependencies from different sources: mix of local artifacts, *OSGi* bundles from P2 update sites and ivy/maven libraries :-)

[See SBT Dependency Manager documentation](http://digimead.github.io/sbt-dependency-manager/).

__Required Java 6 or higher__

Authors
-------

* Alexey Aksenov

License
-------

SBT Dependency Manager is licensed to you under the terms of
the Apache License, version 2.0, a copy of which has been
included in the LICENSE file.
Please check the individual source files for details.

Copyright
---------

Copyright © 2012-2013 Alexey B. Aksenov/Ezh. All rights reserved.

[sc]: http://eed3si9n.com/testing-sbt-plugins
[sp]: https://github.com/digimead/sbt-dependency-manager/tree/master/src/sbt-test/dependency-manager/simple
