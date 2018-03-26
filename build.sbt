name := "protoswole"

version := "0.1"

organization := "org.nbrahms"

// Compilation

scalaVersion := "2.11.11"

scalacOptions ++= Seq("-deprecation", "-feature")

// Project layout

scalaSource in Compile := baseDirectory.value / "src"

scalaSource in Test := baseDirectory.value / "test-src"

sourcesInBase := false

// Dependencies

resolvers ++= Seq(
  "Artifactory at repo.scala-sbt.org" at "http://repo.scala-sbt.org/scalasbt/libs-releases",
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

libraryDependencies ++= Seq(
  "com.google.protobuf" % "protobuf-java" % "2.3.0" % "test",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  scalaVersion("org.scala-lang" % "scala-reflect" % _).value
)

// Macros

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

// Publishing

publishMavenStyle := true

licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT"))

homepage := Some(url("http://github.com/nbrahms/protoswole"))

pomExtra := (
  <scm>
    <url>git@github.com:nbrahms/protoswole.git</url>
    <connection>scm:git:git@github.com:nbrahms/protoswole.git</connection>
  </scm>
  <developers>
    <developer>
      <id>nbrahms</id>
      <name>Nathan Brahms</name>
      <url>http://github.com/nbrahms</url>
    </developer>
  </developers>
)

pomIncludeRepository := { _ => false }

publishTo := Some(Resolver.file("file", new File("releases")))
