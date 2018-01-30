name := "Constellation"

version := "0.1"

scalaVersion := "2.12.4"


libraryDependencies ++= Seq(
  "com.spotify" %% "scio-core" % "0.4.6",
  "com.spotify" %% "scio-test" % "0.4.6" % "test",
  "com.spotify" %% "scio-extra" % "0.4.6",
  "org.apache.beam" % "beam-runners-direct-java" % "2.2.0",
)

libraryDependencies  ++= Seq(
  // Last stable release
  "org.scalanlp" %% "breeze" % "0.13.2",

  // Native libraries are not included by default. add this if you want them (as of 0.7)
  // Native libraries greatly improve performance, but increase jar sizes.
  // It also packages various blas implementations, which have licenses that may or may not
  // be compatible with the Apache License. No GPL code, as best I know.
  "org.scalanlp" %% "breeze-natives" % "0.13.2",

  // The visualization library is distributed separately as well.
  // It depends on LGPL code
  "org.scalanlp" %% "breeze-viz" % "0.13.2"
)


resolvers += "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"

libraryDependencies += "org.scala-graph" %% "graph-core" % "1.12.3"


libraryDependencies += "org.scalatest" % "scalatest_2.12" % "3.0.4" % "test"
