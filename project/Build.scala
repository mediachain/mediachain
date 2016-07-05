import sbt._
import Keys._
import sbtassembly.AssemblyKeys._
import sbtassembly.{MergeStrategy, PathList}
import com.trueaccord.scalapb.{ScalaPbPlugin => PB}
import sbtbuildinfo.BuildInfoPlugin, BuildInfoPlugin._
import sbtbuildinfo.BuildInfoKeys._

object MediachainBuild extends Build {
  updateOptions := updateOptions.value.withCachedResolution(true)

  val specs2Version = "3.7.3"
  val scalaCheckVersion = "1.13.1"

  lazy val publishSettings = Seq(
    publishMavenStyle := true,
    publishArtifact in Test := false,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    pomIncludeRepository := { _ => false },
    licenses := Seq("MIT" -> url("https://raw.githubusercontent.com/mediachain/mediachain/master/LICENSE")),
    homepage := Some(url("https://mediachain.io")),
    pomExtra :=
      <scm>
        <url>git@github.com/mediachain/mediachain.git</url>
        <connection>scm:git:git@github.com/mediachain/mediachain.git</connection>
      </scm>
        <developers>
          <developer>
            <id>yusefnapora</id>
            <name>Yusef Napora</name>
            <url>https://github.com/yusefnapora</url>
          </developer>
          <developer>
            <id>parkan</id>
            <name>Arkadiy Kukarkin</name>
            <url>https://github.com/parkan</url>
          </developer>
          <developer>
            <id>bigs</id>
            <name>Cole Brown</name>
            <url>https://github.com/bigs</url>
          </developer>
          <developer>
            <id>vyzo</id>
            <name>Dimitris Vyzovitis</name>
            <url>https://github.com/vyzo</url>
          </developer>
        </developers>
  )

  override lazy val settings = super.settings ++ Seq(
    organization := "io.mediachain",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "2.11.7",
    scalacOptions ++= Seq("-Xlint", "-deprecation", "-Xfatal-warnings",
      "-feature", "-language:higherKinds"),
    resolvers ++= Seq(
      Resolver.sonatypeRepo("public")
    ),
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats" % "0.4.1",
      "org.typelevel" %% "dogs-core" % "0.2.2",
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


  lazy val transactor = Project("transactor", file("transactor"))
    .settings(settings ++ publishSettings ++ Seq(
      libraryDependencies ++= Seq(
        "io.mediachain" %% "multihash" % "0.1.0",
        "io.atomix.copycat" % "copycat-server" % "1.1.4",
        "io.atomix.copycat" % "copycat-client" % "1.1.4",
        "io.atomix.catalyst" % "catalyst-netty" % "1.1.1",
        "org.slf4j" % "slf4j-api" % "1.7.21",
        "org.slf4j" % "slf4j-simple" % "1.7.21",
        "org.rocksdb" % "rocksdbjni" % "4.5.1",
        "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.11.7",
        "com.amazonaws" % "aws-java-sdk-s3" % "1.11.7",
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

  lazy val protocol = Project("protocol", file("protocol"))
    .settings(settings ++ publishSettings ++ Seq(
      libraryDependencies ++= Seq(
        "io.mediachain" %% "multihash" % "0.1.0",
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

  // aggregate means commands will cascade to the subprojects
  // dependsOn means classes will be available
  lazy val mediachain = (project in file("."))
    .aggregate(transactor, protocol)
    .enablePlugins(BuildInfoPlugin)
    .settings(Seq(
      buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
      buildInfoPackage := "io.mediachain.build",
      // Don't publish an artifact for the root project
      publishArtifact := false,
      // sbt-pgp will choke if there's no repo, even tho it's unsed
      publishTo := Some(Resolver.file("Fake repo to make sbt-pgp happy",
        file("target/fake-repo")))
    ))
}

