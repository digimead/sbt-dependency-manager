resolvers ++= Seq(
  Classpaths.typesafeResolver,
  "oss sonatype" at "https://oss.sonatype.org/content/groups/public/",
  "digimead-maven" at "http://storage.googleapis.com/maven.repository.digimead.org/",
  "jgit-releases-for-sbt-pgp-build" at "http://download.eclipse.org/jgit/maven"
)

addSbtPlugin("org.digimead" % "sbt-dependency-manager" % "0.7-SNAPSHOT")

addSbtPlugin("org.digimead" % "sbt-application" % "0.1.3.0-SNAPSHOT")
