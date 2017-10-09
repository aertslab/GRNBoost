package org.aertslab.grnboost.lab

import org.aertslab.grnboost.Specs.Server
import org.aertslab.grnboost._
import org.apache.spark.sql.functions._
import org.scalatest.{FlatSpec, Matchers}

/**
  * @author Thomas Moerman
  */
class DatasetLab extends FlatSpec with GRNBoostSuiteBase with Matchers {

  behavior of "Dataset"

  it should "filter by set" in {
    import spark.implicits._

    val ds = List(KV("a", 1), KV("b", 2), KV("c", 3)).toDS()

    val filtered = ds.filter(kv => Set("c").contains(kv.key))

    filtered.show()
  }

  it should "calculate max" in {
    import spark.implicits._

    val list: List[RoundsEstimation] = List(
      RoundsEstimation(0, "Gad1", 0.5f, 25),
      RoundsEstimation(0, "Gad1", 0.5f, 55),
      RoundsEstimation(0, "Gad1", 0.5f, 34),
      RoundsEstimation(0, "Gad1", 0.5f, 666),
      RoundsEstimation(0, "Gad1", 0.5f, 320))

    val ds = list.toDS

    val bla = ds.select(max("rounds"))

    println(bla.first.getInt(0))
  }

  it should "roll up a Dataset" taggedAs Server ignore {
    import org.apache.spark.sql.functions._
    import spark.implicits._

    val dream1 =
      spark
        .sparkContext
        .textFile("/media/tmo/data/work/datasets/dream5/out/Network1/part-00000")
        .map(_.split("\t"))
        .map{ case Array(reg, tar, imp) => Regulation(reg, tar, imp.toFloat) }
        .toDS

    val sums = dream1.rollup("target").agg(stddev("gain"), sum("gain"), max("gain"))

    sums.show
  }

}

case class KV(key: String, value: Int)