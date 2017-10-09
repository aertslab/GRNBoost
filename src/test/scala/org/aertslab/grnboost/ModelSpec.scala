package org.aertslab.grnboost

import org.scalatest.{FlatSpec, Matchers}

/**
  * @author Thomas Moerman
  */
class ModelSpec extends FlatSpec with Matchers {

  behavior of "BoosterParamFunctions"

  it should "add default booster params to empty" in {
    val b: BoosterParams = Map.empty

    b.withDefaults shouldBe DEFAULT_BOOSTER_PARAMS
  }

  it should "add default booster params where not present" in {
    val b: BoosterParams = Map("eta" -> 0.3)

    b.withDefaults("eta")    shouldBe 0.3
    b.withDefaults("silent") shouldBe 1
  }

}