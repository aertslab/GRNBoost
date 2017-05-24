package org.aertslab.grnboost.util

import java.lang.System._
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.concurrent.TimeUnit._

import scala.concurrent.duration.Duration

/**
  * @author Thomas Moerman
  */
object TimeUtils {

  def profile[R](block: => R): (R, Duration) = {
    val start  = nanoTime
    val result = block
    val done   = nanoTime

    (result, Duration(done - start, NANOSECONDS))
  }

  def timestamp = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss").format(Calendar.getInstance.getTime)

  def pretty(duration: Duration): String = {
    val s = duration.toSeconds % 60
    val m = (duration.toSeconds / 60) % 60
    val h = (duration.toSeconds / (60 * 60)) % 24
    val d = duration.toSeconds / (60 * 60 * 24)

    (d, h, m, s) match {
      case (0, 0, 0, s) => "%02d seconds".format(s)
      case (0, 0, m, s) => "%02d minutes, %02d seconds".format(m, s)
      case (0, h, m, s) => "%02d hours, %02d minutes, %02d seconds".format(h, m, s)
      case (d, h, m, s) => "%d days, %02d hours, %02d minutes, %02d seconds".format(d, h, m, s)
    }
  }

}