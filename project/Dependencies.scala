/*
 * Copyright (C) 2016-2019 Dataswift Ltd - All Rights Reserved
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * Written by Andrius Aucinas <andrius.aucinas@dataswift.io>, 10 2016
 */

import sbt._

object Dependencies {

  object Versions {
    val crossScala = Seq("2.12.14")
    val scalaVersion = crossScala.head
  }

  val resolvers = Seq(
    "Atlassian Releases" at "https://maven.atlassian.com/public/",
    Resolver.bintrayRepo("scalaz", "releases"),
    Resolver.sonatypeRepo("snapshots"),
    "HAT Library Artifacts Releases" at "https://s3-eu-west-1.amazonaws.com/library-artifacts-releases.hubofallthings.com",
    "HAT Library Artifacts Snapshots" at "https://s3-eu-west-1.amazonaws.com/library-artifacts-snapshots.hubofallthings.com"
  )

  object Library {
    object HATDeX {
      private val version = "2.6.0-SNAPSHOT"
      val hatClient = "org.hatdex" %% "hat-client-scala-play" % "2.6.2-SNAPSHOT"
      val dexClient = "org.hatdex" %% "dex-client-scala-play" % version
      val dexter = "org.hatdex" %% "dexter" % "1.4.3-SNAPSHOT"
      val codegen = "org.hatdex" %% "slick-postgres-driver" % "0.0.9"
    }

    object Play {
      val version = play.core.PlayVersion.current
      val ws = "com.typesafe.play" %% "play-ws" % version
      val cache = "com.typesafe.play" %% "play-cache" % version
      val test = "com.typesafe.play" %% "play-test" % version
      val json = "com.typesafe.play" %% "play-json" % "2.6.9"
      val jsonJoda = "com.typesafe.play" %% "play-json-joda" % "2.6.9"
      val typesafeConfigExtras = "com.iheart" %% "ficus" % "1.4.3"
      val playSlick = "com.typesafe.play" %% "play-slick" % "3.0.3"

      object Jwt {
        private val bouncyCastleVersion = "1.60"
        val bouncyCastle = "org.bouncycastle" % "bcprov-jdk15on" % bouncyCastleVersion
        val bouncyCastlePkix = "org.bouncycastle" % "bcpkix-jdk15on" % bouncyCastleVersion
        val nimbusDsJwt = "com.nimbusds" % "nimbus-jose-jwt" % "4.41.2"
      }

      object Utils {
        val playBootstrap = "com.adrianhurt" %% "play-bootstrap" % "1.2-P26-B3" exclude("org.webjars", "jquery")
        val commonsValidator = "commons-validator" % "commons-validator" % "1.5.0"
        val htmlCompressor = "com.mohiva" %% "play-html-compressor" % "0.6.3"
      }

      object Silhouette {
        val version = "5.0.4"
        val passwordBcrypt = "com.mohiva" %% "play-silhouette-password-bcrypt" % version
        val persistence = "com.mohiva" %% "play-silhouette-persistence" % version
        val cryptoJca = "com.mohiva" %% "play-silhouette-crypto-jca" % version
        val silhouette = "com.mohiva" %% "play-silhouette" % version
      }
    }

    object Specs2 {
      private val version = "3.9.5"
      val core = "org.specs2" %% "specs2-core" % version
      val matcherExtra = "org.specs2" %% "specs2-matcher-extra" % version
      val mock = "org.specs2" %% "specs2-mock" % version
    }

    object Utils {
      val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.0.9"
      val apacheCommonLang = "org.apache.commons" % "commons-lang3" % "3.10"
    }

    object AwsV1 {
      private val version = "1.11.971"
      val awsJavaSesSdk   = "com.amazonaws" % "aws-java-sdk-ses" % version
    }

    val scalaGuice = "net.codingwell" %% "scala-guice" % "4.1.0"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % "2.5.4"
  }
}
