package org.aertslab.grnboost.cases.megacell

import org.apache.spark.sql.SparkSession
import org.aertslab.grnboost.cases.DataReader.readTFs
import org.aertslab.grnboost.util.RankUtils.saveSpearmanCorrelationMatrix
import org.aertslab.grnboost.util.TimeUtils._
import org.aertslab.grnboost.{ExpressionByGene, GRN_BOOST}

import scala.io.Source
import scala.util.Try

/**
  * A script that computes spearman correlation matrix between the TFs and other genes of the
  *
  * @author Thomas Moerman
  */
object MegacellSpearman {

  def main(args: Array[String]): Unit = {
    val parquet        = args(0)
    val mouseTFs       = args(1)
    val out            = args(2)
    val cellSubSetFile = args(3)
    val cellSubSetID   = Try(args(4)).getOrElse("unknown")

    val spark =
      SparkSession
        .builder
        .appName(GRN_BOOST)
        .getOrCreate

    import spark.implicits._

    val (_, duration) = profile {
      val ds = spark.read.parquet(parquet).as[ExpressionByGene].cache
      val TFs = readTFs(mouseTFs).toSet
      val cellIndicesSubSet = Source.fromFile(cellSubSetFile).getLines.filterNot(_.isEmpty).map(_.trim.toInt).toSeq

      val slicedByCells = ds.slice(cellIndicesSubSet)

      saveSpearmanCorrelationMatrix(slicedByCells, TFs, s"$out/spearman.matrix.subset.$cellSubSetID")
    }

    println(s"boosting rounds: ${pretty(duration)}")
  }

}