package org.jetbrains.sbtidea

import org.jetbrains.sbtidea.packaging.PackagingKeys._
import org.jetbrains.sbtidea.tasks._
import sbt.Keys._
import sbt.{Def, file, _}

trait Utils { this: Keys.type =>

  def createRunnerProject(from: ProjectReference, newProjectName: String = ""): Project =
    Project(newProjectName, file(s"target/tools/$newProjectName"))
      .dependsOn(from % Provided)
      .settings(
        name := { if (newProjectName.nonEmpty) newProjectName else name.in(from) + "-runner"},
        scalaVersion := scalaVersion.in(from).value,
        dumpDependencyStructure := null, // avoid cyclic dependencies on products task
        products := packageArtifact.in(from).value :: Nil,  // build the artifact when IDEA delegates "Build" action to sbt shell
        packageMethod := org.jetbrains.sbtidea.packaging.PackagingKeys.PackagingMethod.Skip(),
        unmanagedJars in Compile := intellijMainJars.value,
        unmanagedJars in Compile ++= maybeToolsJar,
        createIDEARunConfiguration := genCreateRunConfigurationTask(from).value,
        autoScalaLibrary := !hasPluginsWithScala(intellijExternalPlugins.all(ScopeFilter(inDependencies(from))).value.flatten)
      ).enablePlugins(SbtIdeaPlugin)

  def genCreateRunConfigurationTask(from: ProjectReference): Def.Initialize[Task[File]] = Def.task {
    implicit  val log: PluginLogger = new SbtPluginLogger(streams.value)
    val configName = name.in(from).value
    val vmOptions = intellijVMOptions.value.copy(debug = false)
    val data = IdeaConfigBuilder.buildRunConfigurationXML(
      name.in(from).value,
      configName,
      name.value,
      vmOptions.asSeq,
      intellijPluginDirectory.value)
    val outFile = baseDirectory.in(ThisBuild).value / ".idea" / "runConfigurations" / s"$configName.xml"
    IO.write(outFile, data.getBytes)
    outFile
  }

}
