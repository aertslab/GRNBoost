package org.aertslab.grnboost.util

import breeze.linalg.{CSCMatrix, SparseVector => BSV}
import org.aertslab.grnboost.util.BreezeUtils._
import org.scalatest.{FlatSpec, Matchers}

/**
  * @author Thomas Moerman
  */
class BreezeUtilsSpec extends FlatSpec with Matchers {

  val em = CSCMatrix(
    (6f, 0f, 0f, 0f),
    (0f, 7f, 0f, 0f),
    (0f, 0f, 0f, 0f),
    (0f, 0f, 8f, 0f),
    (0f, 0f, 0f, 0f),
    (9f, 0f, 0f, 9f))

  "rowIter from a CSCMatrix" should "work" in {
    val empty: Array[(Int, Float)] = Array.empty

    em.sparseVectorIterator.toList shouldBe List(
      BSV(4)((0, 6f)),
      BSV(4)((1, 7f)),
      BSV(4)(empty: _*),
      BSV(4)((2, 8f)),
      BSV(4)(empty: _*),
      BSV(4)((0, 9f), (3, 9f))
    )
  }

  "labeledPoints from a CSCMatrix" should "work" in {
    val ps = em.labeledPointsIterator(Array(1f, 2f, 3f, 4f, 5f, 6f)).toList

    val p0 = ps(0)
    p0.label   shouldBe 1f
    p0.indices shouldBe Array(0)
    p0.values  shouldBe Array(6f)

    val p5 = ps(5)
    p5.label   shouldBe 6f
    p5.indices shouldBe Array(0, 3)
    p5.values  shouldBe Array(9f, 9f)
  }

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