package org.aertslab.grnboost.util

import java.io.File
import java.io.PrintWriter

import org.aertslab.grnboost.Path

/**
  * @author Thomas Moerman
  */
object IOUtils {

  def writeToFile(path: Path, text: String): Unit =
    writeToFile(new File(path), text)

  def writeToFile(file: File, text: String): Unit = {
    val pw = new PrintWriter(file)
    try pw.write(text) finally pw.close()
  }

}