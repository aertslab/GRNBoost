package org.aertslab.grnboost

import org.apache.spark.ml.linalg.Vectors.dense
import org.scalatest.tagobjects.Slow
import org.scalatest.{FlatSpec, Matchers}

/**
  * @author Thomas Moerman
  */
class GRNBoostSpec extends FlatSpec with GRNBoostSuiteBase with Matchers {

  import spark.implicits._

//   FIXME
//
//   private val zeiselMrna = PropsReader.props("zeisel")
//
//  "converting to a CSCMatrix" should "work" taggedAs Slow in {
//    val ds = ZeiselReader.apply(spark, zeiselMrna)
//
//    val predictors = "Tspan12" :: Nil
//
//    val csc = GRNBoost.reduceToRegulatorCSCMatrix(ds, predictors)
//
//    csc.rows shouldBe ZEISEL_CELL_COUNT
//    csc.cols shouldBe 1
//  }

  "small test for CSCMatrix builder" should "work" taggedAs Slow in {
    val ds =
      Seq(
        ExpressionByGene("gene1", dense(1.0, 1.1, 1.2, 1.3)),
        ExpressionByGene("gene2", dense(2.0, 2.1, 2.2, 2.3)),
        ExpressionByGene("gene3", dense(3.0, 3.1, 3.2, 3.3)),
        ExpressionByGene("gene4", dense(4.0, 4.1, 4.2, 4.3)))
      .toDS

    val csc = GRNBoost.reduceToRegulatorCSCMatrix(ds, List("gene2", "gene3"))

    csc.rows shouldBe 4
    csc.cols shouldBe 2
  }

}