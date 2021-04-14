logLevel := Level.Warn

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.6.0")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.25")

// Code quality

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")
addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.2.7")
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.3")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.4")

// Web plugins
addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.4.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.4")
addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.2")
addSbtPlugin("org.irundaia.sbt" % "sbt-sassify" % "1.4.13")
addSbtPlugin("com.typesafe.sbt" % "sbt-uglify" % "2.0.0")

// S3 based SBT resolver
resolvers += "HAT Library Artifacts releases" at "https://s3-eu-west-1.amazonaws.com/library-artifacts-releases.hubofallthings.com"
addSbtPlugin("org.hatdex" % "sbt-slick-postgres-generator" % "0.0.9")
