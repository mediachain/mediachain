import sbt._
import Keys._
import sbtassembly.AssemblyKeys._
import sbtassembly._
import com.trueaccord.scalapb.{ScalaPbPlugin => PB}

object MediachainBuild extends Build {
  updateOptions := updateOptions.value.withCachedResolution(true)

  val specs2Version = "3.7.3"
  val scalaCheckVersion = "1.13.1"

  override lazy val settings = super.settings ++ Seq(
    organization := "io.mediachain",
    version := "0.0.1",
    scalaVersion := "2.11.7",
    scalacOptions ++= Seq("-Xlint", "-deprecation", "-Xfatal-warnings", "-feature"),
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats" % "0.4.1",
      "org.json4s" %% "json4s-jackson" % "3.3.0",
      "org.specs2" %% "specs2-core" % specs2Version % "test",
      "org.specs2" %% "specs2-junit" % specs2Version % "test",
      "org.specs2" %% "specs2-matcher-extra" % specs2Version % "test",
      "org.specs2" %% "specs2-scalacheck" % specs2Version % "test",
      "org.scalacheck" %% "scalacheck" % scalaCheckVersion % "test",
      "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0-RC1"
    ),
    scalacOptions in Test ++= Seq("-Yrangepos")
  )

  // TODO: replace this with maven-published version
  val scalaMultihashCommit = "c21efd1b3534d9a4c5f7b2bc2d971eed0e5a2744"
  lazy val scalaMultihash = RootProject(uri(
    s"git://github.com/mediachain/scala-multihash.git#$scalaMultihashCommit"
  ))

  lazy val transactor = Project("transactor", file("transactor"))
    .settings(settings)
    .dependsOn(scalaMultihash)

  lazy val peer = Project("peer", file("peer"))
    .settings(settings)

  lazy val client = Project("client", file("client"))
    .settings(settings)
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
    fork in Test := true,
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
    unmanagedClasspath in Test += baseDirectory.value / "test-resources"
  )).dependsOn(l_space)
    .dependsOn(l_space % "test->test")
    .dependsOn(core)

  lazy val rpc = Project("rpc", file("rpc")).settings(scalaSettings ++
    PB.protobufSettings ++
    List(

      // tell protobuf compiler to use version 3 syntax
      PB.runProtoc in PB.protobufConfig := (args =>
        com.github.os72.protocjar.Protoc.runProtoc("-v300" +: args.toArray)),

      version in PB.protobufConfig := "3.0.0-beta-2",

      libraryDependencies ++= Seq(
        "io.grpc" % "grpc-all" % "0.9.0",
        "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" %
          (PB.scalapbVersion in PB.protobufConfig).value
      )
    )
  ).dependsOn(l_space)
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

    initialCommands in (Test, console) := "ammonite.repl.Main.run(\"" + predef + "\")"
  )).dependsOn(orientdb_migrations)
    .dependsOn(core)


  // for separating work on CircleCI containers (try to keep these balanced)
  lazy val circle_1 = project
    .aggregate(translation_engine, rpc)
  lazy val circle_2 = project
    .aggregate(core, l_space)

  // aggregate means commands will cascade to the subprojects
  // dependsOn means classes will be available
  lazy val root = (project in file("."))
    .aggregate(core, l_space,
      translation_engine, rpc)
    .dependsOn(core, l_space,
      translation_engine, rpc)
}

