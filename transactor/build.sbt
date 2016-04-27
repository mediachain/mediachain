name := "transactor"
scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
 "io.atomix.copycat" % "copycat-server" % "1.0.0-rc7",
 "io.atomix.copycat" % "copycat-client" % "1.0.0-rc7",
 "io.atomix.catalyst" % "catalyst-netty" % "1.0.7"
)