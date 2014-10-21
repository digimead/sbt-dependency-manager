resolvers ++= Seq(
  Classpaths.typesafeResolver,
  "oss sonatype" at "https://oss.sonatype.org/content/groups/public/",
  "digimead-maven" at "http://storage.googleapis.com/maven.repository.digimead.org/",
  "jgit-releases-for-sbt-pgp-build" at "http://download.eclipse.org/jgit/maven"
)

libraryDependencies <+= (sbtBinaryVersion in update, scalaBinaryVersion in update, baseDirectory) { (sbtV, scalaV, base) =>
  Defaults.sbtPluginExtra("org.digimead" % "sbt-dependency-manager" %
    scala.io.Source.fromFile(base / Seq("..", "version").mkString(java.io.File.separator)).mkString.trim, sbtV, scalaV) }
