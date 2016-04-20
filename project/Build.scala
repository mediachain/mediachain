import sbt.{Tests, _}
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
      "com.github.scopt" %% "scopt" % "3.4.0",
      "com.lihaoyi" % "ammonite-repl" % "0.5.7" % "test" cross CrossVersion.full
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

  val orientdb_migrations_commit = "5f345cefda34f5671e6bb9e6c30312299d11f371"
  lazy val orientdb_migrations = ProjectRef(
    uri("git://github.com/mediachain/orientdb-migrations.git#" +
      orientdb_migrations_commit),
    "orientdb-migrations-root"
  )

  // Projects
  // schema translator/ingester (candidate to spin out into own project)
  lazy val translation_engine = Project("translation_engine", file("translation_engine")).settings(scalaSettings ++ List(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats" % "0.4.1",
      "org.json4s" %% "json4s-jackson" % "3.3.0"
    ),
    unmanagedClasspath in Test += baseDirectory.value / "test-resources",
    // see http://stackoverflow.com/a/9901616
    //
    // instantiating the `SBTSetupHook` and `SBTCleanupHook`
    // classes causes code to run that prepares the testing
    // environment & works around some classloader issues with
    // sbt and orient / gremlin
    testOptions in Test += Tests.Setup(loader => {
      println("test setup")
      loader.loadClass("io.mediachain.SBTSetupHook").newInstance
    }),
    testOptions in Test += Tests.Cleanup(loader => {
      println("test cleanup")
      loader.loadClass("io.mediachain.SBTCleanupHook").newInstance
    })
  )).dependsOn(l_space)
    .dependsOn(l_space % "test->test")
    .dependsOn(core)

  // spray-based API server (may be deprecated in favor of gRPC)
  lazy val api_server = Project("api_server", file("api_server")).settings(scalaSettings ++ List(
    libraryDependencies ++= {
      val akkaV = "2.3.9"
      val sprayV = "1.3.3"
      val json4sV = "3.2.11"

      Seq(
        "io.spray"            %%  "spray-can"     % sprayV,
        "io.spray"            %%  "spray-routing-shapeless2" % sprayV,
        "io.spray"            %%  "spray-httpx"   % sprayV,
        // spray's specs2 support doesn't yet play nice with v 3.x of specs2
        "io.spray"      %%  "spray-testkit" % sprayV  % "test" exclude("org.specs2", "specs2_2.11"),
        "org.parboiled"       %%  "parboiled"     % "2.0.1",
        "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
        "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test",
        "org.json4s" %% "json4s-jackson" % json4sV
      )
    }
  )).dependsOn(l_space)
    .dependsOn(l_space % "test->test")
    .dependsOn(core)

  // core types, errors, etc
  lazy val core = Project("core", file("core")).settings(scalaSettings ++ List(
    libraryDependencies ++= Seq(
      "com.michaelpollmeier" % "gremlin-scala_2.11" % "3.1.1-incubating.1",
      "org.typelevel" %% "cats" % "0.4.1",
      "org.json4s" %% "json4s-jackson" % "3.3.0"
    )
  ))

  // main project
  lazy val predef =  """
  import com.orientechnologies.orient.core.Orient
  import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory
  import gremlin.scala._
  import io.mediachain.Types._
  import io.mediachain.Traversals.{GremlinScalaImplicits, VertexImplicits}
  import io.mediachain.util.orient.MigrationHelper

  Orient.instance.removeShutdownHook()
  lazy val graph = MigrationHelper.newInMemoryGraph()
  """.split("\n").mkString("; ")
  lazy val l_space = Project("l_space", file("l_space")).settings(scalaSettings ++ List(
    mainClass := Some("io.mediachain.LSpace"),

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
      ),

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
      ),

    // see http://stackoverflow.com/a/9901616
    //
    // instantiating the `SBTSetupHook` and `SBTCleanupHook`
    // classes causes code to run that prepares the testing
    // environment & works around some classloader issues with
    // sbt and orient / gremlin
    testOptions in Test += Tests.Setup( loader => {
      println("test setup")
      loader.loadClass("io.mediachain.SBTSetupHook").newInstance
      }),

    initialCommands in (Test, console) := "ammonite.repl.Main.run(\"" + predef + "\")"
    )).dependsOn(orientdb_migrations)
      .dependsOn(core)


    // for separating work on CircleCI containers (try to keep these balanced)
    lazy val circle_1 = project
      .aggregate(translation_engine, api_server)
    lazy val circle_2 = project
      .aggregate(core, l_space)

    // aggregate means commands will cascade to the subprojects
    // dependsOn means classes will be available
    lazy val root = (project in file("."))
      .aggregate(core, l_space,
        translation_engine, api_server)
      .dependsOn(core, l_space,
        translation_engine, api_server)

}
