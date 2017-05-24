package org.aertslab.grnboost.algo

import java.io.File

import org.scalatest.{FlatSpec, Matchers}
import org.aertslab.grnboost.XGBoostRegressionParams
import org.aertslab.grnboost.algo.InferXGBoostRegulations._

import scala.io.Source

/**
  * @author Thomas Moerman
  */
class InferXGBoostRegulationsSpec extends FlatSpec with Matchers {

  behavior of "parsing metrics"

  val treeDumpWithStats = Source.fromFile(new File("src/test/resources/xgb/treeDumpWithStats.txt")).getLines.mkString("\n")

  it should "parse tree metrics" in {
    val gainMap = parseGainScores(treeDumpWithStats)

    gainMap.size shouldBe 28

    val (featureIdx, gain) = gainMap(0)

    featureIdx shouldBe 223
    gain shouldBe 1012.38f
  }

  behavior of "aggregating booster metrics"

  val params = XGBoostRegressionParams()

  it should "aggregate correctly for 1 tree" in {
    val gains = aggregateGainByGene(null)(Seq(treeDumpWithStats))

    gains(223) shouldBe 1012.38f + 53.1558f
  }

  it should "aggregate correctly for multiple trees" in {
    val gains = aggregateGainByGene(null)(Seq(treeDumpWithStats, treeDumpWithStats))

    gains(223) shouldBe 2 * (1012.38f + 53.1558f)
  }

}