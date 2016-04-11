import sbt._
import Keys._
import sbtassembly.AssemblyKeys._
import sbtassembly._

object ApplicationBuild extends Build {
  override lazy val settings = super.settings ++ Seq(
    scalaVersion := "2.11.7"
  )
}

object LSpaceBuild extends Build{
  val lSpaceConfigPath = Option(System.getProperty("LSPACE_CONFIG_PATH"))
    .getOrElse("/etc/lspace/")

  lazy val scalaSettings = Seq(
    scalaVersion := "2.11.7",
    version := "0.0.1-WORKSHOP",
    scalacOptions ++= Seq("-Xlint", "-deprecation", "-Xfatal-warnings", "-feature"),
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % "3.7" % "test",
      "org.specs2" %% "specs2-junit" % "3.7" % "test",
      "org.specs2" %% "specs2-matcher-extra" % "3.7" % "test",
      "org.scalacheck" %% "scalacheck" % "1.13.0" % "test",
      "com.github.scopt" %% "scopt" % "3.4.0"
    ),
    scalacOptions in Test ++= Seq("-Yrangepos")
  )

  assemblyMergeStrategy in assembly := {
    case x if x.endsWith("project.clj") => MergeStrategy.discard // Leiningen build files
    case x if x.toLowerCase.startsWith("meta-inf") => MergeStrategy.discard // More bumf
    case _ => MergeStrategy.first
  }

  Resolver.sonatypeRepo("public")

  updateOptions := updateOptions.value.withCachedResolution(true)

  lazy val l_space = project
    .settings(scalaSettings: _*)
    .dependsOn(orientdb_migrations)
    .dependsOn(core)

  lazy val translation_engine = project
    .settings(scalaSettings: _*)
    .dependsOn(l_space)
    .dependsOn(l_space % "test->test")
    .dependsOn(core)

  lazy val api_server = project
    .settings(scalaSettings: _*)
    .dependsOn(l_space)
    .dependsOn(l_space % "test->test")
    .dependsOn(core)

  lazy val core = project
    .settings(scalaSettings: _*)

  val orientdb_migrations_commit = "5f345cefda34f5671e6bb9e6c30312299d11f371"
  lazy val orientdb_migrations = ProjectRef(
    uri("git://github.com/mediachain/orientdb-migrations.git#" +
      orientdb_migrations_commit),
    "orientdb-migrations-root"
  )


  lazy val root = (project in file("."))
    .aggregate(core, l_space,
      translation_engine)
    .dependsOn(core, l_space,
      translation_engine)
}
