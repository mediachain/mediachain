import sbt._
import Keys._

object MediachainBuild extends Build {
  override lazy val settings = super.settings ++ Seq(
    organization := "io.mediachain",
    version := "0.0.1",
    scalaVersion := "2.11.7"
  )

  lazy val transactor = Project("transactor", file("transactor"))
    .settings(settings)

  lazy val peer = Project("peer", file("peer"))
    .settings(settings)

  lazy val client = Project("client", file("client"))
    .settings(settings)
}
