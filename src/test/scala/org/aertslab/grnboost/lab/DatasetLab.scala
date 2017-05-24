package org.aertslab.grnboost.lab

import org.aertslab.grnboost.GRNBoostSuiteBase
import org.scalatest.{FlatSpec, Matchers}

/**
  * @author Thomas Moerman
  */
class DatasetLab extends FlatSpec with GRNBoostSuiteBase with Matchers {

  behavior of "Dataset"

  it should "filter by set" in {
    import spark.implicits._

    val ds = List(KV("a", 1), KV("b", 2), KV("c", 3)).toDS()

    val pred = Set("c")

    val filtered = ds.filter(kv => pred.contains(kv.key))

    filtered.show()
  }

}

case class KV(key: String, value: Int)