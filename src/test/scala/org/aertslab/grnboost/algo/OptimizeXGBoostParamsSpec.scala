package org.aertslab.grnboost.algo

import java.lang.Math.min

import org.scalatest.{FlatSpec, Matchers}
import org.aertslab.grnboost.GRNBoostSuiteBase
import org.aertslab.grnboost.algo.OptimizeXGBoostHyperParams._

/**
  * @author Thomas Moerman
  */
class OptimizeXGBoostParamsSpec extends FlatSpec with Matchers {

  behavior of "creating folds"

  it should "create fold slices correctly" in {
    foldsSamplesCombos.foreach { case (nrFolds, nrSamples) =>

      val slices = makeFoldSlices(nrFolds, nrSamples)

      slices.keys.size                   shouldBe min(nrFolds, nrSamples)
      slices.flatMap(_._2).size          shouldBe nrSamples
      slices.flatMap(_._2).toList.sorted shouldBe (0 until nrSamples)

      val sizes = slices.map(_._2.size)
      if (sizes.nonEmpty) {
        (sizes.max - sizes.min) should be <= 1
      }
    }
  }

  it should "create CV sets correctly" in {
    foldsSamplesCombos.foreach { case (nrFolds, nrSamples) =>

      val cvSets = makeCVSets(nrFolds, nrSamples)

      cvSets.size shouldBe min(nrFolds, nrSamples)

      cvSets
        .foreach{ case (train, test) =>
          train.isEmpty shouldBe false
          test.isEmpty shouldBe false

          (train.toSet intersect test.toSet) shouldBe Set.empty

          (train.toList ::: test.toList).sorted shouldBe (0 until nrSamples)
        }
    }
  }

  def foldsSamplesCombos = for (nrFolds   <- Seq(2, 10);
                                nrSamples <- Seq(9, 10)) yield (nrFolds, nrSamples)

}