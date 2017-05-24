package org.aertslab.grnboost.util

import org.scalatest.{Matchers, FlatSpec}

/**
  * @author Thomas Moerman
  */
class PropsReaderSpec extends FlatSpec with Matchers {

  "PropsReader" should "pass the smoke test" in {
    PropsReader.show
  }

}