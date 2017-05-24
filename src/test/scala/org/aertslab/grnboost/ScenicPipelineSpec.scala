package org.aertslab.grnboost

import org.apache.spark.ml.linalg.{Matrices, Vectors}
import org.apache.spark.ml.linalg.Vectors.dense
import org.scalatest.{FlatSpec, Matchers}
import org.aertslab.grnboost.cases.zeisel.ZeiselReader
import org.aertslab.grnboost.cases.zeisel.ZeiselReader.ZEISEL_CELL_COUNT
import org.aertslab.grnboost.util.PropsReader

/**
  * @author Thomas Moerman
  */
class ScenicPipelineSpec extends FlatSpec with GRNBoostSuiteBase with Matchers {

  private val zeiselMrna = PropsReader.props("zeisel")

  import spark.implicits._

  "converting to a CSCMatrix" should "work" in {
    val ds = ZeiselReader.apply(spark, zeiselMrna)

    val predictors = "Tspan12" :: Nil

    val csc = GRNBoost.reduceToRegulatorCSCMatrix(ds, predictors)

    csc.rows shouldBe ZEISEL_CELL_COUNT
    csc.cols shouldBe 1

    println(csc.toDense.t)
  }

  "small test for CSCMatrix builder" should "work" in {
    val ds =
      Seq(
        ExpressionByGene("gene1", dense(1.0, 1.1, 1.2, 1.3)),
        ExpressionByGene("gene2", dense(2.0, 2.1, 2.2, 2.3)),
        ExpressionByGene("gene3", dense(3.0, 3.1, 3.2, 3.3)),
        ExpressionByGene("gene4", dense(4.0, 4.1, 4.2, 4.3)))
      .toDS

    val csc = GRNBoost.reduceToRegulatorCSCMatrix(ds, List("gene2", "gene3"))

    println(csc)

    // val m = Matrices.dense(4, 2, Array(2.0, 2.1, 2.2, 2.3, 3.0, 3.1, 3.2, 3.3))
  }

}