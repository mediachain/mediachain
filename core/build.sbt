name := "core"

version := "0.0.1-WORKPRINT"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.michaelpollmeier" % "gremlin-scala_2.11" % "3.1.1-incubating.1",
  "org.typelevel" %% "cats" % "0.4.1",
  "org.json4s" %% "json4s-jackson" % "3.3.0"
)

