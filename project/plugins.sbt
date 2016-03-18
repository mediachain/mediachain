resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

logLevel := Level.Warn

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.2")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.3.5")
addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.1.0")
