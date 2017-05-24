package org.aertslab.grnboost

import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.sql.types.FloatType
import org.scalatest.{FlatSpec, Matchers}

/**
  * @author Thomas Moerman
  */
class CoreSpec extends FlatSpec with GRNBoostSuiteBase with Matchers {

  import spark.implicits._

  behavior of "Dataset of ExpressionByGene"

  it should "slice" in {
    val ds =
      Seq(
        ExpressionByGene("Dlx1", Vectors.sparse(5, Seq((1, 1d), (3, 3d)))),
        ExpressionByGene("Dlx2", Vectors.sparse(5, Seq((2, 2d), (4, 4d)))))
      .toDS
    
    val sliced = ds.slice(Seq(0, 1))

    sliced.head.values.toDense.toArray shouldBe Array(0d, 1d)
  }

  behavior of "randomCellIndices"

  it should "return a random subset of a small range" in {
    val selection = randomSubset(5, 0 until 10)
    selection.size shouldBe 5
  }

  it should "return a random subset of a large range" in {
    val selection = randomSubset(100000, 0 until 1300000)
    selection.size shouldBe 100000
  }

  behavior of "regulation dataset"

  it should "aggregate with another regulation dataset" in {
    import org.apache.spark.sql.functions._

    val ds1 =
      Seq(
        RawRegulation("Dlx1", "Tspan2", 3f),
        RawRegulation("Olig1", "Myrf", 6f))
      .toDS

    val ds2 =
      Seq(
        RawRegulation("Dlx1", "Tspan2", 7f),
        RawRegulation("Olig1", "Myrf", 2f),
        RawRegulation("Dlx2", "Bla", 1))
      .toDS

    Seq(ds1, ds2)
      .reduce(_ union _)
      .groupBy($"regulator", $"target")
      .agg(sum($"gain").as("sum_gain"))
      .withColumn("gain", $"sum_gain" / 5)
      .select($"regulator", $"target", $"gain".cast(FloatType))
      .as[Regulation]
      .show
  }

}