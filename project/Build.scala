import sbt._
import Keys._
import sbtassembly.AssemblyKeys._
import sbtassembly._
import complete.DefaultParsers._
import sbt.complete._

object ApplicationBuild extends Build {
  override lazy val settings = super.settings ++ Seq(
    scalaVersion := "2.11.7"
  )
}

object LSpaceBuild extends Build{
  val lSpaceConfigPath = Option(System.getProperty("LSPACE_CONFIG_PATH"))
    .getOrElse("/etc/lspace/")

  val partners: List[Parser[String]] = List("moma", "tate")
  val partnerParser = partners.reduce(_ | _)

  val acceptArgs = (' ' ~> partnerParser ~ (' ' ~> StringBasic))

  lazy val importDump = inputKey[Unit]("Import static data dump")
  val importDumpTask = importDump := {
    val args: (String, String) = acceptArgs.parsed
    println(args)
  }

  lazy val scalaSettings = Seq(
    scalaVersion := "2.11.7",
    version := "0.0.1-WORKSHOP",
    scalacOptions ++= Seq("-Xlint", "-deprecation", "-Xfatal-warnings"),
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % "3.7" % "test",
      "org.specs2" %% "specs2-junit" % "3.7" % "test",
      "org.scalacheck" %% "scalacheck" % "1.13.0" % "test",
      "com.github.scopt" %% "scopt" % "3.4.0"
    ),
    scalacOptions in Test ++= Seq("-Yrangepos"),
    importDumpTask
  )

  assemblyMergeStrategy in assembly := {
    case x if x.endsWith("project.clj") => MergeStrategy.discard // Leiningen build files
    case x if x.toLowerCase.startsWith("meta-inf") => MergeStrategy.discard // More bumf
    case _ => MergeStrategy.first
  }

  Resolver.sonatypeRepo("public")

  lazy val l_space = project
    .settings(scalaSettings: _*)

  lazy val translation_engine = project
    .settings(scalaSettings: _*)
    .dependsOn(l_space)
    .dependsOn(l_space % "test->test")

  lazy val root = (project in file("."))
    .aggregate(l_space,
      translation_engine)
}

