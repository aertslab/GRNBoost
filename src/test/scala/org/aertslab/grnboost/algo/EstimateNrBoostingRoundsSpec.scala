package org.aertslab.grnboost.algo

import breeze.linalg.CSCMatrix
import org.aertslab.grnboost.Expression
import org.aertslab.grnboost.algo.EstimateNrBoostingRounds._
import org.scalatest.{FlatSpec, Matchers}
import org.aertslab.grnboost.util.BreezeUtils._

/**
  * @author Thomas Moerman
  */
class EstimateNrBoostingRoundsSpec extends FlatSpec with Matchers {

  behavior of "cvSet"

  val builder = new CSCMatrix.Builder[Expression](rows = 99, cols = 4)
  (0 until 99).foreach(r =>
    builder.add(r, 0, r.toFloat)
  )

  val csc = builder.result
  val dm = csc.copyToUnlabeledDMatrix

  it should "slice the matrix correctly in train and test set" in {
    val map = indicesByFold(3, csc.rows)

    val (train_0, test_0) = cvSet(0, map, dm)
    val (train_1, test_1) = cvSet(1, map, dm)
    val (train_2, test_2) = cvSet(2, map, dm)

    Seq(train_0, train_1, train_2) foreach { _.rowNum shouldBe 66 }
    Seq(test_0, test_1, test_2)    foreach { _.rowNum shouldBe 33 }
  }

  behavior of "indicesByFold"

  it should "work for 2 folds" in {
    val map = indicesByFold(nrFolds = 2, nrSamples = 100)

    map.size shouldBe 2

    map(0).size shouldBe 50
    map(1).size shouldBe 50
  }

  it should "work for 3 folds" in {
    val map = indicesByFold(nrFolds = 3, nrSamples = 99)

    map.size shouldBe 3

    map(0).size shouldBe 33
    map(1).size shouldBe 33
    map(2).size shouldBe 33
  }

}