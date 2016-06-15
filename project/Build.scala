import sbt._
import Keys._
import sbtassembly.AssemblyKeys._
import sbtassembly.{MergeStrategy,PathList}
import com.trueaccord.scalapb.{ScalaPbPlugin => PB}

object MediachainBuild extends Build {
  updateOptions := updateOptions.value.withCachedResolution(true)

  val specs2Version = "3.7.3"
  val scalaCheckVersion = "1.13.1"

  override lazy val settings = super.settings ++ Seq(
    organization := "io.mediachain",
    version := "0.0.1",
    scalaVersion := "2.11.7",
    scalacOptions ++= Seq("-Xlint", "-deprecation", "-Xfatal-warnings",
      "-feature", "-language:higherKinds"),
    // resolvers += Resolver.mavenLocal, // local maven for tip debugging
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats" % "0.4.1",
      "org.json4s" %% "json4s-jackson" % "3.3.0",
      "org.specs2" %% "specs2-core" % specs2Version % "test",
      "org.specs2" %% "specs2-junit" % specs2Version % "test",
      "org.specs2" %% "specs2-matcher-extra" % specs2Version % "test",
      "org.specs2" %% "specs2-scalacheck" % specs2Version % "test",
      "org.scalacheck" %% "scalacheck" % scalaCheckVersion % "test",
      "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0-RC1",
      "com.github.scopt" %% "scopt" % "3.4.0",
      "com.lihaoyi" % "ammonite-repl" % "0.5.7" % "test" cross CrossVersion.full
    ),
    scalacOptions in Test ++= Seq("-Yrangepos"),
    test in assembly := {}
  )

  lazy val utils = Project("utils", file("utils"))
    .settings(settings)

  // TODO: replace this with maven-published version
  val scalaMultihashCommit = "f8ddda5c98ff0d73fdcadfc8a66332cb22f9c23b"
  lazy val scalaMultihash = RootProject(uri(
    s"git://github.com/mediachain/scala-multihash.git#$scalaMultihashCommit"
  ))

  lazy val transactor = Project("transactor", file("transactor"))
    .settings(settings ++ Seq(
      libraryDependencies ++= Seq(
        "io.atomix.copycat" % "copycat-server" % "1.1.0",
        "io.atomix.copycat" % "copycat-client" % "1.1.0",
        "io.atomix.catalyst" % "catalyst-netty" % "1.1.1",
        "org.slf4j" % "slf4j-api" % "1.7.21",
        "org.slf4j" % "slf4j-simple" % "1.7.21",
        "org.rocksdb" % "rocksdbjni" % "4.5.1",
        "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.7",
        "com.amazonaws" % "aws-java-sdk-s3" % "1.11.7"
        "com.github.scopt" %% "scopt" % "3.4.0"
      ),
      assemblyMergeStrategy in assembly := {
        case "META-INF/io.netty.versions.properties" => 
          MergeStrategy.filterDistinctLines
        case x => 
          val oldStrategy = (assemblyMergeStrategy in assembly).value
          oldStrategy(x)
      },
      mainClass := Some("io.mediachain.transactor.Main")
    ))
    .dependsOn(protocol)
    .dependsOn(scalaMultihash)

  Resolver.sonatypeRepo("public")

  updateOptions := updateOptions.value.withCachedResolution(true)

  // TODO: use maven version
  val orientdb_migrations_commit = "5f345cefda34f5671e6bb9e6c30312299d11f371"
  lazy val orientdb_migrations = ProjectRef(
    uri("git://github.com/mediachain/orientdb-migrations.git#" +
      orientdb_migrations_commit),
    "orientdb-migrations-root"
  )

  lazy val protocol = Project("protocol", file("protocol"))
    .settings(settings ++ Seq(
      libraryDependencies ++= Seq(
        "co.nstant.in" % "cbor" % "0.7"
      )) ++
      PB.protobufSettings ++
      Seq(

        // tell protobuf compiler to use version 3 syntax
        PB.runProtoc in PB.protobufConfig := (args =>
          com.github.os72.protocjar.Protoc.runProtoc("-v300" +: args.toArray)),

        version in PB.protobufConfig := "3.0.0-beta-2",

        libraryDependencies ++= Seq(
          "io.grpc" % "grpc-all" % "0.14.0",
          "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" %
            (PB.scalapbVersion in PB.protobufConfig).value
        )
      )
    )
    .dependsOn(scalaMultihash)

  // schema translator/ingester (candidate to spin out into own project)
  lazy val translation_engine = Project("translation_engine", file("translation_engine")).settings(settings ++ List(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats" % "0.4.1",
      "org.json4s" %% "json4s-jackson" % "3.3.0"
    ),
    unmanagedClasspath in Test += baseDirectory.value / "test-resources"
  )).dependsOn(l_space)
    .dependsOn(l_space % "test->test")
    .dependsOn(core)

  lazy val rpc = Project("rpc", file("rpc")).settings(settings)
    .dependsOn(l_space)
    .dependsOn(l_space % "test->test")
    .dependsOn(protocol)
    .dependsOn(core)

  // core types, errors, etc (L-SPACE only, FIXME -- remove!!!)
  lazy val core = Project("core", file("core")).settings(settings ++ List(
    libraryDependencies ++= Seq(
      "com.michaelpollmeier" % "gremlin-scala_2.11" % "3.1.1-incubating.1",
      "org.typelevel" %% "cats" % "0.4.1",
      "org.json4s" %% "json4s-jackson" % "3.3.0"
    )
  ))

  lazy val predef =  """
  import com.orientechnologies.orient.core.Orient
  import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory
  import gremlin.scala._
  import io.mediachain.Types._
  import io.mediachain.Traversals.{GremlinScalaImplicits, VertexImplicits}
  import io.mediachain.util.orient.MigrationHelper

  lazy val graph = MigrationHelper.newInMemoryGraph()
                     """.split("\n").mkString("; ")
  lazy val l_space = Project("l_space", file("l_space")).settings(settings ++ List(
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
  

  // aggregate means commands will cascade to the subprojects
  // dependsOn means classes will be available
  lazy val mediachain = (project in file("."))
    .aggregate(core, l_space,
      translation_engine, rpc, transactor, protocol)
}

