name := "l_space"

version := "0.0.1-WORKPRINT"

scalaVersion := "2.11.7"

mainClass := Some("io.mediachain.LSpace")

libraryDependencies ++= Seq(
  "com.michaelpollmeier" % "gremlin-scala_2.11" % "3.1.1-incubating.1",
  "com.michaelpollmeier" % "orientdb-gremlin" % "3.1.0-incubating.1",
  "com.tinkerpop.blueprints" % "blueprints-core" % "2.6.0",
  "org.typelevel" %% "cats" % "0.4.1",
  "com.chuusai" %% "shapeless" % "2.2.5",
  "org.json4s" %% "json4s-jackson" % "3.2.11",
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % "2.4.0",
  "org.bouncycastle" % "bcprov-jdk15on" % "1.54",
  "org.apache.directory.studio" % "org.apache.commons.codec" % "1.8"
)

excludeDependencies ++= Seq(
  SbtExclusionRule("commons-logging", "commons-logging"),
  SbtExclusionRule("org.codehaus.groovy", "groovy"),
  SbtExclusionRule("org.codehaus.groovy", "groovy-groovysh"),
  SbtExclusionRule("org.codehaus.groovy", "groovy-console"),
  SbtExclusionRule("org.codehaus.groovy", "groovy-templates"),
  SbtExclusionRule("org.codehaus.groovy", "groovy-xml"),
  SbtExclusionRule("org.codehaus.groovy", "groovy-swing"),
  SbtExclusionRule("org.codehaus.groovy", "groovy-json"),
  SbtExclusionRule("org.codehaus.groovy", "groovy-jsr223")
)

// see http://stackoverflow.com/a/9901616
//
// instantiating the `SBTSetupHook` and `SBTCleanupHook`
// classes causes code to run that prepares the testing
// environment & works around some classloader issues with
// sbt and orient / gremlin
testOptions in Test += Tests.Setup( loader => {
  println("test setup")
  loader.loadClass("io.mediachain.SBTSetupHook").newInstance
})

testOptions in Test += Tests.Cleanup( loader => {
  println("test cleanup")
  loader.loadClass("io.mediachain.SBTCleanupHook").newInstance
})

initialCommands in console :=
  """
    import com.orientechnologies.orient.core.Orient
    import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory
    import gremlin.scala._
    import io.mediachain.Types._
    import io.mediachain.Traversals.{GremlinScalaImplicits, VertexImplicits}
    import io.mediachain.util.orient.MigrationHelper

    Orient.instance.removeShutdownHook()
    lazy val graph = MigrationHelper.newInMemoryGraph()
    println("It's a UNIX system! I know this!")
  """

cleanupCommands in consoleProject :=
  """
    graph.close()
    Orient.instance.shutdown()
  """
