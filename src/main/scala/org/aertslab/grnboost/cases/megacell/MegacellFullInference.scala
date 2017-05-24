package org.aertslab.grnboost.cases.megacell

import org.apache.spark.sql.SparkSession
import org.aertslab.grnboost.cases.DataReader.readTFs
import org.aertslab.grnboost.util.IOUtils.writeToFile
import org.aertslab.grnboost.util.TimeUtils.{pretty, profile}
import org.aertslab.grnboost.{ExpressionByGene, GRNBoost, GRN_BOOST, XGBoostRegressionParams}

/**
  * @author Thomas Moerman
  */
object MegacellFullInference {

  val boosterParams = Map(
    "seed"              -> 777,
    "eta"               -> 0.01,
    "subsample"         -> 0.8,
    "colsample_bytree"  -> 0.25,
    "max_depth"         -> 1,
    "silent" -> 1
  )

  def main(args: Array[String]): Unit = {

    val parquet          = args(0)
    val mouseTFs         = args(1)
    val out              = args(2)
    val nrCellsPerPhase  = args(3).toInt
    val nrBoostingRounds = args(4).toInt
    val nrPartitions     = args(5).toInt
    val nrThreads        = args(6).toInt
    val nrSkipPhases     = args(7).toInt

    val parsedArgs =
      s"""
         |Args:
         |* parquet            = $parquet
         |* mouseTFs           = $mouseTFs
         |* output             = $out
         |* nr cells per phase = $nrCellsPerPhase
         |* nr boosting rounds = $nrBoostingRounds
         |* nr partitions      = $nrPartitions
         |* nr xgb threads     = $nrThreads
         |* skip nr phases     = $nrSkipPhases
      """.stripMargin

    val infoFile   = s"$out/full.stumps.$nrBoostingRounds.cells.per.phase.$nrCellsPerPhase.info.txt"
    val timingFile = s"$out/full.stumps.$nrBoostingRounds.cells.per.phase.$nrCellsPerPhase.info.txt"

    println(parsedArgs)
    writeToFile(infoFile, parsedArgs + "\nbooster params:\n" + boosterParams.mkString("\n") + "\n")

    val spark =
      SparkSession
        .builder
        .appName(GRN_BOOST)
        .getOrCreate

    import spark.implicits._

    val (phaseCount, duration) = profile {

      val ds = spark.read.parquet(parquet).as[ExpressionByGene].cache
      val TFs = readTFs(mouseTFs).toSet

      val totalCellCount = ds.head.values.size

      val phaseCellSets =
        (0 until totalCellCount)
          .sliding(nrCellsPerPhase, nrCellsPerPhase)
          .toList
          .reverse match {
            case x :: y :: rest => y ++ x :: rest
            case _              => ???
          }

      println(s"Executing ${phaseCellSets.size} phases with sizes: ${phaseCellSets.map(_.size).mkString(", ")}")

      phaseCellSets
        .drop(nrSkipPhases)
        .zipWithIndex
        .foreach{ case (phaseCellIndices, phaseIndex) => {

          println(s"Executing phase $phaseIndex with ${phaseCellIndices.size} cell indices")

          val phaseDS = ds.slice(phaseCellIndices)

          val params =
            new XGBoostRegressionParams(
              nrRounds = nrBoostingRounds,
              boosterParams = boosterParams)

          val outDir = s"$out/full.stumps.$nrBoostingRounds.rounds.phase.${phaseIndex + nrSkipPhases}"

          GRNBoost
            .inferRegulations(
              phaseDS,
              candidateRegulators = TFs,
              params = params,
              nrPartitions = Some(nrPartitions))
            .sort($"regulator", $"target", $"gain".desc)
            .repartition(1)
            .write
            .parquet(outDir)
        }}

      phaseCellSets.size
    }

    val timingInfo =
      s"""
         |* finised inferring network in ${phaseCount} phases.
         |* wall time: ${pretty(duration)}
       """.stripMargin

    println(timingInfo)
    writeToFile(timingFile, timingInfo)
  }


}