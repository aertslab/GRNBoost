package org.aertslab.grnboost.cases.megacell

import org.apache.spark.sql.SparkSession
import org.joda.time.DateTime.now
import org.aertslab.grnboost._
import org.aertslab.grnboost.cases.DataReader._
import org.aertslab.grnboost.util.IOUtils.writeToFile
import org.aertslab.grnboost.util.TimeUtils._

/**
  * @author Thomas Moerman
  */
object MegacellInference {

  val boosterParams = Map(
    "seed"              -> 777,
    "eta"               -> 0.01,
    "subsample"         -> 0.8,
    "colsample_bytree"  -> 0.25,
    "max_depth"         -> 1,
    "silent" -> 1
  )

  val nrRounds = 250

  val params =
    XGBoostRegressionParams(
      nrRounds = nrRounds,
      boosterParams = boosterParams)

  def main(args: Array[String]): Unit = {

    val parquet      = args(0)
    val mouseTFs     = args(1)
    val out          = args(2)
    val nrCells      = if (args(3).toUpperCase == "ALL") None else Some(args(3).toInt)
    val nrTargets    = if (args(4).toUpperCase == "ALL") None else Some(args(4).toInt)
    val nrPartitions = args(5).toInt
    val nrThreads    = args(6).toInt

    val parsedArgs =
      s"""
        |Args:
        |* parquet         = $parquet
        |* mouseTFs        = $mouseTFs
        |* output          = $out
        |* nr cells        = $nrCells
        |* nr target genes = $nrTargets
        |* nr partitions   = $nrPartitions
        |* nr xgb threads  = $nrThreads
      """.stripMargin

    val outDir     = s"$out/stumps.$nrRounds.cells.${nrCells.getOrElse("ALL")}.${now}"
    val sampleFile = s"$out/stumps.$nrRounds.cells.${nrCells.getOrElse("ALL")}.obs.sample.txt"
    val infoFile   = s"$out/stumps.$nrRounds.cells.${nrCells.getOrElse("ALL")}.targets.param.info.txt"
    val timingFile = s"$out/stumps.$nrRounds.cells.${nrCells.getOrElse("ALL")}.targets.timing.info.txt"

    println(parsedArgs)
    writeToFile(infoFile, parsedArgs + "\nbooster params:\n" + boosterParams.mkString("\n") + "\n")

    val spark =
      SparkSession
        .builder
        .appName(GRN_BOOST)
        .getOrCreate

    import spark.implicits._

    val (_, duration) = profile {

      val ds = spark.read.parquet(parquet).as[ExpressionByGene].cache
      val TFs = readTFs(mouseTFs).toSet

      val totalCellCount = ds.head.values.size

      val cellIndicesSubset = nrCells.map(nr => randomSubset(nr, 0 until totalCellCount))
      cellIndicesSubset.foreach(subset => writeToFile(sampleFile, subset.sorted.mkString("\n")))

      val slicedByCells =
        cellIndicesSubset
          .map(subset => ds.slice(subset).cache)
          .getOrElse(ds)

      val targetSet =
        nrTargets
          .map(nr => slicedByCells.take(nr).map(_.gene).toSet)
          .getOrElse(Set.empty)

      val regulations =
        GRNBoost
          .inferRegulations(
            slicedByCells,
            candidateRegulators = TFs,
            params = params.copy(
              boosterParams = params.boosterParams + (XGB_THREADS -> nrThreads)),
            targets = targetSet,
            nrPartitions = Some(nrPartitions))
          .cache
      
      regulations
        .sort($"regulator", $"target", $"gain".desc)
        .rdd
        .map(r => s"${r.regulator}\t${r.target}\t${r.gain}")
        .repartition(1)
        .saveAsTextFile(outDir)
    }

    val timingInfo =
      s"results written to $outDir\n" +
      s"Wall time with ${params.nrRounds} boosting rounds: ${pretty(duration)}"

    println(timingInfo)
    writeToFile(timingFile, timingInfo)
  }

}