ThisBuild / scalaVersion := "2.13.16"
ThisBuild / version      := "0.1.0"
ThisBuild / organization := "dashygo097"

val chiselVersion = "7.0.0"
ThisBuild / resolvers += Resolver.file("local-ivy", file(Path.userHome + "/.ivy2/local"))(Resolver.ivyStylePatterns)

ThisBuild / scalacOptions ++= Seq(
  "-language:reflectiveCalls",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Xcheckinit",
  "-Ymacro-annotations"
)

lazy val core = (project in file("src/core"))
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "dashygo097" %% "utils"  % "0.1.0",
      "dashygo097" %% "dds"    % "0.1.0",
      "dashygo097" %% "dsp"    % "0.1.0",
      "dashygo097" %% "math"   % "0.1.0",
      "dashygo097" %% "com"    % "0.1.0",
      "dashygo097" %% "mem"    % "0.1.0",
      "dashygo097" %% "perip"  % "0.1.0",
      "dashygo097" %% "mod"    % "0.1.0",
      
      "org.chipsalliance" %% "chisel" % chiselVersion,
    ),
    Compile / unmanagedSourceDirectories += baseDirectory.value,
    addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full),
  )
