import sbt.Keys._

val akkaVersion = "2.5.4"

lazy val root = (project in file("."))
        .settings(
          name := "mower-scala",
          version := "1.1",
          scalaVersion := "2.12.3",
          libraryDependencies ++= Seq(
            "org.slf4j" % "slf4j-simple" % "1.7.25" withSources(),
            "com.typesafe.akka" %% "akka-typed" % akkaVersion withSources(),
            "com.typesafe.akka" %% "akka-typed-testkit" % akkaVersion % "test" withSources(),
            "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test" withSources(),
            "org.scalatest" %% "scalatest" % "3.0.1" % "test" withSources()
          )
        )

cancelable in Global := true