import sbt._
import Keys._
import sbtassembly.AssemblyKeys._
import sbtassembly.{MergeStrategy, PathList}
import com.trueaccord.scalapb.{ScalaPbPlugin => PB}
import com.typesafe.sbt.GitVersioning
import com.typesafe.sbt.SbtGit.git
import sbtbuildinfo.{BuildInfoKey, BuildInfoOption, BuildInfoPlugin}
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
    test in assembly := {},

    // sbt-buildinfo configuration
    buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion, sbtVersion,
      BuildInfoKey.map(name) { case (k, v) => k -> s"io.mediachain.$v" }
    ),
    buildInfoOptions := Seq(BuildInfoOption.ToMap, BuildInfoOption.ToJson)
  )


  lazy val transactor = Project("transactor", file("transactor"))
    .enablePlugins(BuildInfoPlugin)
    .settings(settings ++ publishSettings ++ Seq(
      buildInfoPackage := "io.mediachain.transactor",
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
    .enablePlugins(BuildInfoPlugin)
    .settings(settings ++ publishSettings ++ Seq(
      buildInfoPackage := "io.mediachain.protocol",
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
    .enablePlugins(GitVersioning)
    .settings(Seq(
      // Don't publish an artifact for the root project
      publishArtifact := false,
      // sbt-pgp will choke if there's no repo, even tho it's unused
      publishTo := Some(Resolver.file("Fake repo to make sbt-pgp happy",
        file("target/fake-repo"))),

      // git versioning configuration
      git.useGitDescribe := true,

      // by default sbt-git will append -SNAPSHOT if your working dir has
      // uncommitted changes.  We want to disable that and add -SNAPSHOT
      // to indicate a build that's "in-between" releases
      git.uncommittedSignifier := None,

      // If there are no matching tags prior to the head commit, set the version
      // number to 0.1.0-${sha}-SNAPSHOT where ${sha} is the first 7 chars
      // of the head sha1 hash.
      git.formattedShaVersion := git.gitHeadCommit.value.map { sha =>
        s"0.1.0-${sha.take(7)}-SNAPSHOT"
      },

      // Convert git tags to version numbers
      // Matches tags beginning with the pattern "vX.X.X", where X is an
      // integer.
      // If we're on a tagged commit, this function will receive just the
      // tag itself, which is used as the version number directly.
      // If we're not on a tagged commit, the function gets the output of
      // `git describe --tags`, and we return the most recent tag + the
      // sha of the current commit, e.g. `0.42.0-fe432ab-SNAPSHOT`
      git.gitTagToVersionNumber := {
        case GitDescribeRegex(taggedVersion, commitsSinceTag, sha) =>
          Some(s"$taggedVersion-$sha-SNAPSHOT")

        case TagOnlyRegex(taggedVersion) =>
          Some(taggedVersion)

        case _ => None
      }

    ))

  lazy val GitDescribeRegex = "v([0-9]+\\.[0-9]+\\.[0-9]+.*)-([0-9]+)-g([0-9a-fA-F]+)".r
  lazy val TagOnlyRegex = "v([0-9]+\\.[0-9]+\\.[0-9]+.*)".r
}

