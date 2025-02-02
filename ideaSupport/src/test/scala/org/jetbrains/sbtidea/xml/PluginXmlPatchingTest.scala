package org.jetbrains.sbtidea.xml

import java.nio.file.{Files, StandardCopyOption}

import org.jetbrains.sbtidea.ConsoleLogger
import org.jetbrains.sbtidea.download.IdeaMock
import org.jetbrains.sbtidea.packaging.artifact
import org.scalatest.{FunSuite, Matchers}

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

private class PluginXmlPatchingTest extends FunSuite with Matchers with IdeaMock with ConsoleLogger {

  private def getPluginXml = {
    val tmpFile = createTempFile("", "plugin.xml")
    artifact.using(getClass.getResourceAsStream("plugin.xml")) { stream =>
      Files.copy(stream, tmpFile, StandardCopyOption.REPLACE_EXISTING)
    }
    tmpFile
  }

  test("patch all fields test") {
    val options = pluginXmlOptions { xml =>
      xml.version           = "VERSION"
      xml.changeNotes       = "CHANGE_NOTES"
      xml.pluginDescription = "DESCRIPTION"
      xml.sinceBuild        = "SINCE_BUILD"
      xml.untilBuild        = "UNTIL_BUILD"
    }
    val pluginXml = getPluginXml
    val initialContent = Files.readAllLines(pluginXml).asScala
    val patcher = new PluginXmlPatcher(pluginXml)
    val result = patcher.patch(options)
    val newContent = Files.readAllLines(result).asScala

    val diff = newContent.toSet &~ initialContent.toSet

    diff.size shouldBe 4
    diff should contain (s"""    <change-notes>${options.changeNotes}</change-notes>""")
    diff should contain (s"""    <version>${options.version}</version>""")
    diff should contain (s"""    <description>${options.pluginDescription}</description>""")
    diff should contain (s"""    <idea-version since-build="${options.sinceBuild}" until-build="${options.untilBuild}"/>""")
  }

  test("patch only since fields test") {
    val options = pluginXmlOptions { xml =>
      xml.version           = "VERSION"
      xml.changeNotes       = "CHANGE_NOTES"
      xml.pluginDescription = "DESCRIPTION"
      xml.sinceBuild        = "SINCE_BUILD"
    }
    val pluginXml = getPluginXml
    val initialContent = Files.readAllLines(pluginXml).asScala
    val patcher = new PluginXmlPatcher(pluginXml)
    val result = patcher.patch(options)
    val newContent = Files.readAllLines(result).asScala

    val diff = newContent.toSet &~ initialContent.toSet

    diff.size shouldBe 4
    diff should contain (s"""    <change-notes>${options.changeNotes}</change-notes>""")
    diff should contain (s"""    <version>${options.version}</version>""")
    diff should contain (s"""    <description>${options.pluginDescription}</description>""")
    diff should contain (s"""    <idea-version since-build="${options.sinceBuild}"/>""")
  }

  test("patch only until fields test") {
    val options = pluginXmlOptions { xml =>
      xml.version           = "VERSION"
      xml.changeNotes       = "CHANGE_NOTES"
      xml.pluginDescription = "DESCRIPTION"
      xml.untilBuild        = "UNTIL_BUILD"
    }
    val pluginXml = getPluginXml
    val initialContent = Files.readAllLines(pluginXml).asScala
    val patcher = new PluginXmlPatcher(pluginXml)
    val result = patcher.patch(options)
    val newContent = Files.readAllLines(result).asScala

    val diff = newContent.toSet &~ initialContent.toSet

    diff.size shouldBe 4
    diff should contain (s"""    <change-notes>${options.changeNotes}</change-notes>""")
    diff should contain (s"""    <version>${options.version}</version>""")
    diff should contain (s"""    <description>${options.pluginDescription}</description>""")
    diff should contain (s"""    <idea-version until-build="${options.untilBuild}"/>""")
  }

  test("no modifications done if no patching options are defined") {
    val options = pluginXmlOptions { _ => }
    val pluginXml = getPluginXml
    val initialContent = Files.readAllLines(pluginXml).asScala
    val patcher = new PluginXmlPatcher(pluginXml)
    val result = patcher.patch(options)
    val newContent = Files.readAllLines(result).asScala

    val diff = newContent.toSet &~ initialContent.toSet

    diff.size shouldBe 0
  }

}
