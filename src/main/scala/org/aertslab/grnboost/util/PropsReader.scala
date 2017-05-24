package org.aertslab.grnboost.util

import java.io.File

import scala.io.Source
import scala.util.Try

/**
  * @author Thomas Moerman
  */
object PropsReader {

  private[this] val RESOURCES_PATH = "src/test/resources/"
  private[this] val CONFIG_PATH    = RESOURCES_PATH + "config/"
  private[this] val PROFILE_NAME   = "profile.props"
  private[this] val PROFILE_PATH   = CONFIG_PATH + PROFILE_NAME

  /**
    * @return Returns the properties for the current profile.
    */
  def props: Map[String, String] =
    currentProfile
      .map(profile => {
        val extension = if (profile.endsWith(".props")) "" else ".props"

        s"$CONFIG_PATH$profile$extension" })
      .flatMap(readProps)
      .get

  /**
    * @param file
    * @return Returns the properties for specified file.
    */
  def readProps(file: String): Try[Map[String, String]] = Try {
    Source
      .fromFile(new File(file))
      .getLines
      .filterNot { line =>
        line.startsWith("#") ||
        line.trim.isEmpty }
      .map(_.split("=").map(_.trim).take(2))
      .map{ case Array(k, v) => (k, v) }
      .toMap
  }

  def currentProfile = Try { readProps(PROFILE_PATH).get("profile") }

  def availableProfiles =
    new File(CONFIG_PATH)
      .listFiles
      .map(_.getName)
      .filterNot(_ == PROFILE_NAME)

  def info =
    s"""
       |current profile: ${currentProfile.get}
       |
       |available profiles
       |------------------
       |${availableProfiles.mkString("\n")}
       |
       |current props
       |-------------
       |${props.mkString("\n")}
      """.stripMargin

  def show = println(info)

}