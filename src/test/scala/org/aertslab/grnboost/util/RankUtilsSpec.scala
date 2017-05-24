package org.aertslab.grnboost.util

import org.aertslab.grnboost.GRNBoostSuiteBase
import org.aertslab.grnboost.util.RankUtils.toRankings
import org.apache.commons.math3.stat.correlation.{PearsonsCorrelation, SpearmansCorrelation}
import org.scalatest._

/**
  * @author Thomas Moerman
  */
class RankUtilsSpec extends FlatSpec with GRNBoostSuiteBase with Matchers {

  behavior of "toRankings"

  it should "work for empty list" in {
    val empty = List[Float]()

    toRankings(empty) shouldBe Nil
  }

  it should "work for a singleton" in {
    val single = List(1f)

    toRankings(single) shouldBe Seq(1)
  }

  it should "work for a small list" in {
    val list = List(0, 2, 3, 3, 0, 8, 8)

    toRankings(list) shouldBe Seq(2, 3, 4, 5, 1, 6, 7)
  }

  it should "work for a list of same things" in {
    val list = List(1, 1, 1, 1, 1)

    toRankings(list).toSet shouldBe (1 to 5).toSet
  }

  behavior of "correlation calculation"

  it should "equal Pearson on ranks" in {
    val a = Array(2d, 10d, 3d, 7d)
    val b = Array(8d,  1d, 5d, 9d)

    val sp1 = new SpearmansCorrelation().correlation(a, b)

    val sp2 = new PearsonsCorrelation().correlation(toRankings(a).toArray, toRankings(b).toArray)

    sp1 shouldBe sp2
  }

}