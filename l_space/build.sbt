name := "l_space"

version := "0.0.1-WORKPRINT"

scalaVersion := "2.11.7"

mainClass := Some("org.mediachain.LSpace")

libraryDependencies ++= Seq(
  "com.michaelpollmeier" % "gremlin-scala_2.11" % "3.1.0-incubating.1",
  "com.michaelpollmeier" % "orientdb-gremlin" % "3.1.0-incubating.1",
  "com.tinkerpop.blueprints" % "blueprints-core" % "2.6.0",
  "org.typelevel" %% "cats" % "0.4.1",
  "com.chuusai" %% "shapeless" % "2.2.5",
  "org.specs2" %% "specs2-core" % "3.7" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.0" % "test"
)


// see http://stackoverflow.com/a/9901616
//
// instantiating the `SBTSetupHook` and `SBTCleanupHook`
// classes causes code to run that prepares the testing
// environment & works around some classloader issues with
// sbt and orient / gremlin
testOptions in Test += Tests.Setup( loader => {
  println("test setup")
  loader.loadClass("org.mediachain.SBTSetupHook").newInstance
})

testOptions in Test += Tests.Cleanup( loader => {
  println("test cleanup")
  loader.loadClass("org.mediachain.SBTCleanupHook").newInstance
})
