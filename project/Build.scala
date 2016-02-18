import sbt._
import Keys._
import sbtassembly.AssemblyKeys._
import sbtassembly._

object LSpaceBuild extends Build{
  val lSpaceConfigPath = Option(System.getProperty("LSPACE_CONFIG_PATH"))
    .getOrElse("/etc/lspace/")

  lazy val scalaSettings = Seq(
    scalaVersion := "2.11.7",
    version := "0.0.1-WORKSHOP",
    scalacOptions := Seq("-Xlint", "-deprecation", "-Xfatal-warnings")
  )

  assemblyMergeStrategy in assembly := {
    case x if x.endsWith("project.clj") => MergeStrategy.discard // Leiningen build files
    case x if x.toLowerCase.startsWith("meta-inf") => MergeStrategy.discard // More bumf
    case _ => MergeStrategy.first
  }

  Resolver.sonatypeRepo("public")

  lazy val l_space = project
    .settings(scalaSettings: _*)

  lazy val root = (project in file("."))
    .aggregate()
}
