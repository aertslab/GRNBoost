package org.aertslab.grnboost.util

import java.io.File
import java.io.PrintWriter

/**
  * @author Thomas Moerman
  */
object IOUtils {

  def writeToFile(file: String, s: String): Unit = {
    val pw = new PrintWriter(new File(file))
    try pw.write(s) finally pw.close()
  }

}