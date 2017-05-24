package org.aertslab.grnboost.util

import breeze.linalg.CSCMatrix
import org.scalatest.{FlatSpec, Matchers}

import BreezeUtils._

/**
  * @author Thomas Moerman
  */
class BreezeUtilsSpec extends FlatSpec with Matchers {

  "dropping a column from a CSCMatrix" should "work" in {
    val csc = CSCMatrix(
      (6, 0, 0, 0),
      (0, 7, 0, 0),
      (0, 0, 0, 0),
      (0, 0, 8, 0),
      (0, 0, 0, 0),
      (9, 0, 0, 9))

    csc.dropColumn(0) shouldBe CSCMatrix(
      (0, 0, 0),
      (7, 0, 0),
      (0, 0, 0),
      (0, 8, 0),
      (0, 0, 0),
      (0, 0, 9))

    csc.dropColumn(1) shouldBe CSCMatrix(
      (6, 0, 0),
      (0, 0, 0),
      (0, 0, 0),
      (0, 8, 0),
      (0, 0, 0),
      (9, 0, 9))

    csc.dropColumn(2) shouldBe CSCMatrix(
      (6, 0, 0),
      (0, 7, 0),
      (0, 0, 0),
      (0, 0, 0),
      (0, 0, 0),
      (9, 0, 9))

    csc.dropColumn(3) shouldBe CSCMatrix(
      (6, 0, 0),
      (0, 7, 0),
      (0, 0, 0),
      (0, 0, 8),
      (0, 0, 0),
      (9, 0, 0))
  }

}