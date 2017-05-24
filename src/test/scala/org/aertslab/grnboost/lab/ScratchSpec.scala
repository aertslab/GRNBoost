package org.aertslab.grnboost.lab

import java.util.Calendar

import org.scalatest.{Matchers, FlatSpec}

/**
  * @author Thomas Moerman
  */
class ScratchSpec extends FlatSpec with Matchers {

  "a set" should "behave like a fn" in {
    Set("a", "b").apply("a")
  }

  "meh" should "meh" in {
    val bla = Calendar.getInstance.getTime.formatted("yyyy.MM.dd.hh")
    println(bla)
  }

  "sliding window evaluation" should "be lazy" in {
    val r =
      (0 until 10)
        .toStream
        .map(i => {println(i); i})
        .sliding(3, 1)
        .takeWhile(_.last <= 77)

    println(r.mkString(" -- "))
  }

  "merging maps" should "blah" in {
    val m1 = Map("a" -> 0, "b" -> 1)
    val m2 = Map("c" -> 2, "d" -> 3)

    val result = (m2 /: m1)((m, pair) => m + pair)

    println(result)
  }

}