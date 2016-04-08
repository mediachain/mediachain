name := "api_server"

version := "0.0.1-WORKPRINT"

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
