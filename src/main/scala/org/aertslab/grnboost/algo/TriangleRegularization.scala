package org.aertslab.grnboost.algo

import breeze.linalg.{DenseVector, _}
import breeze.numerics._
import breeze.numerics.constants.Pi
import org.aertslab.grnboost.Count

import scala.collection.immutable.Stream.{continually, fill}

/**
  * @author Thomas Moerman
  */
object TriangleRegularization {

  val DEFAULT_PRECISION = 0.001
  val DEFAULT_X_SCALE = 10
  val DEFAULT_STREAK = 3

  /**
    * @param values The list of values in which to find the inflection point.
    * @param precision The precision of the radian angle.
    * @param xScale a multiplication factor to make the right-most point "further away".
    * @param streak Length of a "streak", a.k.a. a list of consecutive Pi values.
    *
    *               Specifies how many times we want to have a PI angle in a row. Makes the algorithm more robust to
    *               small "kinks" in the values where e.g. two consecutive values make an angle with the last value
    *               before the actual inflection point beyond which the angles are all PI.
    *
    * @return Returns the index of the inflection point where consecutive values form a straight line with the last value.
    *
    *         The inflection point is the first point that makes an angle equal to PI (in radians) with its successor
    *         and the last point in the data set.
    */
  def inflectionPointIndex(values: List[Float],
                           precision: Double = DEFAULT_PRECISION,
                           xScale: Int = DEFAULT_X_SCALE,
                           streak: Int = DEFAULT_STREAK): Option[Int] = values match {
    case Nil => None
    case _ :: Nil => None
    case _ :: _ :: Nil => None
    case _ :: _ :: _ :: Nil => None
    case _ =>
      val min = values.min
      val max = values.max
      val minMaxScaled = values.map(g => (g - min) / (max - min))

      val c = DenseVector(values.length.toFloat * xScale, minMaxScaled.last)

      val ZERO: Option[((Boolean, Int), Count)] = None

      minMaxScaled
        .sliding(2, 1)
        .toSeq
        .zipWithIndex
        .map {
          case (va :: vb :: _, i) =>
            val a = DenseVector(i.toFloat,      va)
            val b = DenseVector(i.toFloat + 1f, vb)
            val theta = angle(a, b, c)

            (~=(theta, Pi, precision), i)

          case _ => ??? // a.k.a. ka-boom
        }
        // find first streak
        .scanLeft(ZERO){ case (acc, (current, idx)) =>
          acc
            .map{ case ((prev, _), count) => ((current, idx), if (prev == current) count + 1 else 1) }
            .orElse(Some((current, 0), 1))
        }
        .dropWhile{
          case Some(((isPi: Boolean, _), count)) => (! isPi) || count < streak
          case _                                 => true
        }
        .headOption
        .flatten
        .map{ case ((_, idx), _) => idx }
  }

  /**
    * @param list
    * @param precision
    * @return Returns a Seq of inclusion labels (1=in, 0=out) from the inflection point.
    */
  def labels(list: List[Float],
             precision: Double = DEFAULT_PRECISION,
             streak: Int = DEFAULT_STREAK): Seq[Int] = {

    val idxOpt = inflectionPointIndex(list, precision, streak).map(_ - streak + 1)

    labelStream(idxOpt).take(list.size)
  }

  /**
    * @param inflectionPointIndex
    * @return Returns an infinite Stream of inclusion labels (1=in, 0=out) from the inflection point.
    */
  protected def labelStream(inflectionPointIndex: Option[Int]): Stream[Int] =
    inflectionPointIndex
      .map(fill(_)(1) ++ continually(0))
      .getOrElse(continually(1))

  def ~=(x: Double, y: Double, precision: Double): Boolean = (x - y).abs < precision

  type V = DenseVector[Float]

  /**
    * @return Returns the radian angle between three Gain vectors.
    */
  def angle(a: V, b: V, c: V): Double = {
    val x = a - b
    val y = c - b

    val cos_angle = (x dot y) / (norm(x) * norm(y))

    acos(cos_angle)
  }

}