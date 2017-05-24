package org.aertslab.grnboost.util

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation
import org.apache.spark.sql.Dataset

import scala.util.Random
import org.aertslab.grnboost._

/**
  * @author Thomas Moerman
  */
object RankUtils {

  type Position = Int
  type Rank     = Double

  /**
    * @param values The values to turn into ranks.
    * @param base The base (0, 1) rank, default == 1.
    * @param rng A random number generator for shuffling.
    * @tparam V Generic type for ordered values
    * @return Returns a Seq of ranks.
    */
  def toRankings[V](values: Seq[V],
                    base: Rank = 1,
                    rng: Random = random(777))(implicit o: Ordering[V]): Seq[Rank] =
    values
      .zipWithIndex
      .sortBy{ case (v, p) => v }
      .zipWithIndex
      .groupBy{ case ((v, p), r) => v }
      .foldLeft(List[(Position, Rank)]()){ case (acc, (v, group)) => {
        val ranks = group.map{ case ((v, p), r) => r + base }
        val shuffledRanks = rng.shuffle(ranks)

        val positions = group.map{ case ((v, p), r) => p }
        val shuffledPositions = rng.shuffle(positions)

        acc ++ (shuffledPositions zip shuffledRanks)
      }}
      .sortBy{ case (p, _) => p}
      .map(_._2)

  /**
    * @param ds
    * @param tfs
    * @param out
    */
  def saveSpearmanCorrelationMatrix(ds: Dataset[ExpressionByGene],
                                    tfs: Set[Gene],
                                    out: Path): Unit = {
    val spark = ds.sparkSession

    val tf_ranks =
      ds
        .filter(e => tfs.contains(e.gene))
        .rdd
        .map(e => (e.gene, toRankings(e.values.toDense.toArray).toArray))
        .cache

    val gene_ranks =
      ds
        .rdd
        .map(e => (e.gene, toRankings(e.values.toDense.toArray).toArray))
        .cache

    val header = spark.sparkContext.parallelize(
      Seq(".\t" + gene_ranks.map(_._1).collect.sorted.mkString("\t"))
    )

    val lines =
      (tf_ranks cartesian gene_ranks)
        .map(toCorr)
        .groupBy(_.tf)
        .map{ case (tf, it) =>
          val line = it.toSeq.sortBy(_.gene).map(_.corr).mkString("\t")

          s"$tf\t$line"
        }

    (header union lines)
      .repartition(1)
      .saveAsTextFile(out)
  }

  case class Corr(tf: Gene, gene: Gene, corr: Float)

  private def toCorr(tuple: ((Gene, Array[Double]), (Gene, Array[Double]))) = {
    val ((tf, tf_ranks), (gene, gene_ranks)) = tuple

    if (tf == gene)
      Corr(tf, gene, 1f)
    else
      Corr(tf, gene, new PearsonsCorrelation().correlation(tf_ranks, gene_ranks).toFloat)
  }

}