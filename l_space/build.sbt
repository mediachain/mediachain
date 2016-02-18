name := "l_space"

version := "0.0.1-WORKPRINT"

scalaVersion := "2.11.7"

mainClass := Some("org.mediachain.LSpace")

libraryDependencies ++= Seq(
  "com.michaelpollmeier" % "gremlin-scala_2.11" % "3.1.0-incubating.1",
  "org.typelevel" %% "cats" % "0.4.1"
)