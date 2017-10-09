package org.aertslab.grnboost.lab

import breeze.linalg._
import org.scalatest.{FlatSpec, Matchers}
import org.aertslab.grnboost.Expression

/**
  * @author Thomas Moerman
  */
class BreezeLab extends FlatSpec with Matchers {

  "get a column from a CSC Matrix" should "work" in {
    val b1 = new CSCMatrix.Builder[Int](rows = 4, cols = 4)
    b1.add(0,0,1)
    b1.add(1,1,2)
    b1.add(2,2,3)
    b1.add(3,3,4)
    val m1 = b1.result

    println(m1.toDense)
    println

    val c3 = m1.apply(0 until m1.rows, 2)

    print(c3)
  }

  "adding two CSC matrices" should "work" in {
    val b1 = new CSCMatrix.Builder[Int](rows = 4, cols = 4)
    b1.add(0,0,1)
    b1.add(0,1,1)
    b1.add(0,2,1)
    b1.add(0,3,1)
    val m1 = b1.result

    val b2 = new CSCMatrix.Builder[Int](rows = 4, cols = 4)
    b2.add(3,0,7)
    b2.add(3,1,7)
    b2.add(3,2,7)
    b2.add(3,3,7)
    val m2 = b2.result

    val m3 = m1 + m2

    println(m1.toDense)
    println
    println(m2.toDense)
    println
    println(m3.toDense)
  }

  "slicing a dense vector" should "work" in {
    val v = Array(1f, 2f, 3f, 4f, 5f)
    val d = new DenseVector[Float](v)
    val s = d.apply(Seq(2, 3))

    println(s.toArray.toList)
  }

  "slicing rows of a CSC matrix" should "work" in {
    val m = new CSCMatrix.Builder[Int](rows = 4, cols = 4)
    m.add(0,0,1)
    m.add(1,1,1)
    m.add(2,2,1)
    m.add(3,3,1)
    val m1 = m.result

    println(m1.toDense)
    println(" --- ")

    val s = m1(Seq(1, 2), 0 until m1.cols)

    println(s.toDenseMatrix)
  }

  "sparse vector covariance" should "work" in {
    val v1 = SparseVector(1f, 0f, 0f)
    val v2 = SparseVector(0f, 1f, 0f)

    println(pearson(v1, v2))
    println(pearsonSign(v1, v2))

    // See https://gist.github.com/tbertelsen/4353d4a8a4386afb0abb
  }

  import breeze.linalg._
  import breeze.stats._
  import scala.math.sqrt

  def pearson(a: SparseVector[Expression], b: SparseVector[Expression]): Double = {
    if (a.length != b.length)
      throw new IllegalArgumentException("Vectors not of the same length.")

    val n = a.length

    val aDotB = a.dot(b)
    val aDotA = a.dot(a)
    val bDotB = b.dot(b)
    val aMean = mean(a)
    val bMean = mean(b)

    // See Wikipedia http://en.wikipedia.org/wiki/Pearson_product-moment_correlation_coefficient#For_a_sample
    (aDotB - n * aMean * bMean ) / ( sqrt(aDotA - n * aMean * aMean)  * sqrt(bDotB - n * bMean * bMean) )
  }

  def pearsonSign(a: SparseVector[Expression], b: SparseVector[Expression]): Int = {
    assert(a.length == b.length, s"Vectors not of the same length (${a.length} != ${b.length})")

    val n = a.length
    val aDotB = a dot b
    val meanA = mean(a)
    val meanB = mean(b)

    // See Wikipedia http://en.wikipedia.org/wiki/Pearson_product-moment_correlation_coefficient#For_a_sample
    val r = aDotB - n * meanA * meanB

    Math.signum(r).toInt
  }

}
