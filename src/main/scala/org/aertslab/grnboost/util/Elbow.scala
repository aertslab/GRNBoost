package org.aertslab.grnboost.util

import scala.collection.immutable.Stream.continually

/**
  * @author Thomas Moerman
  */
object Elbow {

  def apply(y: Seq[Double], sensitivity: Double = 0.5d) = {
    val x = (0 until y.size)

    new JKneedle(sensitivity)
      .detectElbowPoints(
        x.map(_.toDouble).toArray,
        y.reverse.toArray)
      .toList
      .map(i => y.size -i -1)
      .reverse
  }

  /**
    * @param elbows
    * @return Returns a lazy Stream of Elbow Option instances.
    */
  def toElbowGroups(elbows: List[Int]): Stream[Option[Int]] =
    (0 :: elbows.map(_ + 1)) // include the data point at index
      .sliding(2, 1)
      .zipWithIndex
      .flatMap {
        case (a :: b :: Nil, i) => Seq.fill(b-a)(Some(i))
        case _                  => Nil // makes match exhaustive
      }
      .toStream ++ continually(None)

}