import sbt.Keys.{libraryDependencies, resolvers}

scalaVersion := "2.12.4"


val scalatestVersion = "3.0.1"
val paradiseVersion = "2.1.0"

val assemblySettings = Seq(
  test in assembly := {}
)
val commonSettings = assemblySettings ++ Seq(
  libraryDependencies += "org.scalatest" %% "scalatest" % scalatestVersion % "test",

  scalacOptions ++= Seq("-language:implicitConversions", "-language:higherKinds", "-language:postfixOps", "-language:existentials"),
  scalacOptions ++= Seq("-explaintypes", "-unchecked", "-deprecation", "-feature", "-Xfatal-warnings"),
//  scalacOptions ++= Seq("-Ymacro-debug-lite"),

  // Build documentation
  scalacOptions in(Compile, doc) ++= Seq("-groups", "-implicits"),

  // For when something goes super wrong with scalac
  //scalacOptions ++= Seq("-Ytyper-debug"),

  //paradise
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += Resolver.sonatypeRepo("releases"),
  addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full),


  libraryDependencies ++= Seq(

    "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0",
    "ch.qos.logback" % "logback-classic" % "1.2.3",

    "org.scala-lang" % "scala-compiler" % "2.12.4"
  ),


  resolvers += "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",

  libraryDependencies += "org.scala-graph" %% "graph-core" % "1.12.3",
  libraryDependencies += "org.scala-graph" %% "graph-dot" % "1.12.1"
)

val beamSettings = commonSettings ++ Seq(
  libraryDependencies ++= Seq(
    "com.spotify" %% "scio-core" % "0.4.6",
    "com.spotify" %% "scio-test" % "0.4.6",
    "com.spotify" %% "scio-extra" % "0.4.6",
    "org.apache.beam" % "beam-runners-direct-java" % "2.2.0",

    // Last stable release
    "org.scalanlp" %% "breeze" % "0.13.2",

    // Native libraries are not included by default. add this if you want them (as of 0.7)
    // Native libraries greatly improve performance, but increase jar sizes.
    // It also packages various blas implementations, which have licenses that may or may not
    // be compatible with the Apache License. No GPL code, as best I know.
    "org.scalanlp" %% "breeze-natives" % "0.13.2",
  )
)

lazy val spatial = ProjectRef(uri("https://github.com/stanford-ppl/spatial-lang.git#SpatialGenSpatial"), "spatial")

lazy val constellation = (project in file("constellation"))
  .settings(commonSettings)

lazy val patterns = (project in file("patterns"))
  .settings(commonSettings).dependsOn(constellation)

lazy val beambackend = (project in file("beam-backend"))
  .settings(beamSettings).dependsOn(constellation, patterns)

lazy val spatialbackend = (project in file("spatial-backend"))
  .settings(commonSettings).dependsOn(constellation, spatial, patterns)

lazy val constellationapps = (project in file("constellation-apps"))
    .settings(commonSettings).dependsOn(constellation, spatialbackend, beambackend)
