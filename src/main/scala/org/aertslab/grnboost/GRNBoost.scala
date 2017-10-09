package org.aertslab.grnboost

import java.io.File
import java.lang.Math.min

import breeze.linalg.CSCMatrix
import org.aertslab.grnboost.DataReader._
import org.aertslab.grnboost.algo._
import org.aertslab.grnboost.util.TimeUtils._
import org.apache.commons.io.FileUtils.{copyFile, deleteDirectory}
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{Column, Dataset, Encoder, SparkSession}
import org.apache.spark.util.SizeEstimator
import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.joda.time.format.DateTimeFormat

import scala.Console.{err, out}
import scala.reflect.ClassTag
import scala.util.{Random, Try}

/**
  * The top-level GRNBoost functions.
  *
  * @author Thomas Moerman
  */
object GRNBoost {

  val ABOUT =
    s"""
      |$GRN_BOOST
      |--------
      |
      |$URL
    """.stripMargin

  /**
    * Main application entry point.
    *
    * @param args The driver program's arguments, an Array of Strings interpreted by the CLI (command line interface)
    *             function, which transforms the args into an Option of Config. If a valid configuration is produced,
    *             a GRNBoost run is performed. Otherwise, feedback is printed to the Java console for user inspection.
    */
  def main(args: Array[String]): Unit =
    CLI(args: _*) match {
      case Some(Config(Some(xgbConfig))) => apply(xgbConfig)
      case Some(Config(None))            => out.print(ABOUT)
      case _                             => err.print("Input validation failure occurred, see error message above.")
    }

  /**
    * Main GRNBoost procedure.
    *
    * @param xgbConfig The configuration parsed from the command line arguments.
    *
    * @return Returns a tuple of possibly updated config and parameter value objects.
    *         This return value is inspected by test routines, but ignored in the main GRNBoost function.
    */
  def apply(xgbConfig: XGBoostConfig): (XGBoostConfig, XGBoostRegressionParams) = {
    import xgbConfig._

    val started = now

    // initialize a Spark session

    val spark =
      SparkSession
        .builder
        .appName(GRN_BOOST)
        .getOrCreate

    // read the full or sub-sampled dataset

    lazy val expressionsByGene = {
      val ds = readExpressionsByGene(spark, inputPath.get, skipHeaders, delimiter, missing)

      sampleSize
        .map(nrCells => ds.subSample(nrCells)._2)
        .getOrElse(ds)
        .cache
    }

    // create the broadcast variables

    lazy val (regulatorsBroadcast, regulatorCSCBroadcast) = {
      val candidateRegulators = readRegulators(spark, regulatorsPath.get)

      createRegulatorBroadcasts(expressionsByGene, candidateRegulators)
    }

    // start with default parameters

    val protoParams =
      XGBoostRegressionParams(
        nrFolds = nrFolds,
        boosterParams = boosterParams)

    // update the inference parameters with boosting rounds estimation

    val (finalXgbConfig, finalParams) = runMode match {
      case CFG_RUN | INF_RUN => updateConfigsWithEstimation(expressionsByGene, regulatorsBroadcast, regulatorCSCBroadcast, xgbConfig, protoParams)
      case _                 => (xgbConfig, protoParams)
    }

    // infer the regulations

    val regulations = runMode match {
      case INF_RUN => Some(inferRegulations(expressionsByGene, regulatorsBroadcast, regulatorCSCBroadcast, xgbConfig, finalParams))
      case _       => None
    }

    // write inference results

    regulations.foreach(_.saveTxt(resultOutput(outputPath.get), includeFlags, delimiter))

    // optionally write a report

    if (report) {
      val cscBroadcastToMeasure = runMode match {
        case DRY_RUN => None
        case _       => Some(regulatorCSCBroadcast.value)
      }

      writeReports(spark, outputPath.get, makeReport(started, finalXgbConfig, cscBroadcastToMeasure, finalParams))
    }

    // move outputs if possible

    moveOutputs(outputPath.get)

    // output for tests

    (finalXgbConfig, finalParams)
  }

  /**
    * @return Returns the configured or default parallelism.
    */
  private[grnboost] def parallelism(spark: SparkSession, xgbConfig: XGBoostConfig) =
    xgbConfig
      .nrPartitions
      .orElse(Some(spark.sparkContext.defaultParallelism))

  /**
    * Overloaded counterpart for testing purposes.
    */
  private[grnboost] def updateConfigsWithEstimation(expressionsByGene: Dataset[ExpressionByGene],
                                                    candidateRegulators: Set[Gene],
                                                    targetGenes: Set[Gene] = Set.empty,
                                                    params: XGBoostRegressionParams,
                                                    nrPartitions: Option[Count] = None): (XGBoostConfig, XGBoostRegressionParams) = {

    val (regulatorsBroadcast, regulatorCSCBroadcast) = createRegulatorBroadcasts(expressionsByGene, candidateRegulators)

    val xgbConfig = XGBoostConfig(targets = targetGenes)

    updateConfigsWithEstimation(expressionsByGene, regulatorsBroadcast, regulatorCSCBroadcast, xgbConfig, params)
  }

  /**
    * @param expressionsByGene The expressions by gene Dataset.
    * @param regulatorsBroadcast The List of regulator genes broadcast variable.
    * @param regulatorCSCBroadcast The CSCMatrix broadcast variable.
    * @param xgbConfig The config parsed from the command line.
    * @param params The proto params.
    *
    * @return Returns a tuple of XGBoostConfig and XGBoostRegressionParams params, with updated boosting rounds value.
    */
  def updateConfigsWithEstimation(expressionsByGene: Dataset[ExpressionByGene],
                                  regulatorsBroadcast: Broadcast[List[Gene]],
                                  regulatorCSCBroadcast: Broadcast[CSCMatrix[Expression]],
                                  xgbConfig: XGBoostConfig,
                                  params: XGBoostRegressionParams): (XGBoostConfig, XGBoostRegressionParams) = {

    import xgbConfig._

    val spark = expressionsByGene.sparkSession
    import spark.implicits._

    def nrPartitions = parallelism(spark, xgbConfig)

    def estimateNrRounds(ds: Dataset[ExpressionByGene], estimationSet: Either[FoldNr, Set[Gene]]) = {
      val estimationTargets = estimationSet match {
        case Left(estimationTargetSetSize) =>
          new Random(params.seed)
            .shuffle(ds.genes)
            .take(min(estimationTargetSetSize, ds.count).toInt)
            .toSet

        case Right(estimationTargetSet) =>
          estimationTargetSet
      }

      val targetsOnlyDS = ds.filter(e => estimationTargets contains e.gene)

      val taskFactory = EstimateNrBoostingRoundsIterated(params)(_, _)

      val roundsEstimations = computeMapped(targetsOnlyDS, regulatorsBroadcast, regulatorCSCBroadcast, nrPartitions)(taskFactory)

      val estimatedNrRounds = aggregateEstimate(roundsEstimations)

      (estimatedNrRounds, estimationTargets)
    }

    val (finalNrRounds, estimationTargets): (Option[FoldNr], Set[Gene]) = (nrBoostingRounds, estimationSet) match {
      case (None, set)   => estimateNrRounds(expressionsByGene, set)
      case (nrRounds, _) => (nrRounds, Set.empty)
    }

    val updatedParams =
      params
        .copy(nrRounds = finalNrRounds)

    val updatedXgbConfig =
      xgbConfig
        .copy(estimationSet    = Right(estimationTargets))
        .copy(nrBoostingRounds = finalNrRounds)
        .copy(nrPartitions     = nrPartitions)

    (updatedXgbConfig, updatedParams)
  }

  /**
    * Overloaded counterpart for testing purposes.
    */
  private[grnboost] def inferRegulations(expressionsByGene: Dataset[ExpressionByGene],
                                         candidateRegulators: Set[Gene],
                                         targetGenes: Set[Gene] = Set.empty,
                                         params: XGBoostRegressionParams,
                                         nrPartitions: Option[Count] = None,
                                         iterated: Boolean = false): Dataset[Regulation] = {

    val (regulatorsBroadcast, regulatorCSCBroadcast) = createRegulatorBroadcasts(expressionsByGene, candidateRegulators)

    val xgbConfig = XGBoostConfig(targets = targetGenes, iterated = iterated)

    inferRegulations(expressionsByGene, regulatorsBroadcast, regulatorCSCBroadcast, xgbConfig, params)
  }

  /**
    * @param expressionsByGene The expressions by gene Dataset.
    * @param regulatorsBroadcast The List of regulator genes broadcast variable.
    * @param regulatorCSCBroadcast The CSCMatrix broadcast variable.
    * @param xgbConfig The config parsed from the command line.
    * @param params The proto params.
    *
    * @return Returns a Dataset of regulations..
    */
  def inferRegulations(expressionsByGene: Dataset[ExpressionByGene],
                       regulatorsBroadcast: Broadcast[List[Gene]],
                       regulatorCSCBroadcast: Broadcast[CSCMatrix[Expression]],
                       xgbConfig: XGBoostConfig,
                       params: XGBoostRegressionParams): Dataset[Regulation] = {
    import xgbConfig._

    val spark = expressionsByGene.sparkSession
    import spark.implicits._

    def targetsOnly(ds: Dataset[ExpressionByGene]) =
      if (targets.isEmpty)
        ds
      else
        ds.filter(e => targets contains e.gene)

    def nrPartitions = parallelism(spark, xgbConfig)

    def infer(ds: Dataset[ExpressionByGene]) =
      if (iterated) {
        val taskFactory = InferRegulationsIterated(params)(_, _)

        computeMapped(ds, regulatorsBroadcast, regulatorCSCBroadcast, nrPartitions)(taskFactory)
      } else {
        val partitionTaskFactory = InferRegulations(params)(_, _, _)

        computePartitioned(ds, regulatorsBroadcast, regulatorCSCBroadcast, nrPartitions)(partitionTaskFactory)
      }

    def regularize(ds: Dataset[Regulation]) =
      if (regularized)
        withRegularizationLabels(ds, params).filter($"include" === 1)
      else
        withRegularizationLabels(ds, params)

    def normalize(ds: Dataset[Regulation]) =
      if (normalized)
        normalizedByAggregate(ds)
      else
        ds

    def truncate(ds: Dataset[Regulation]) =
      truncated
        .map(nr => ds.sort($"gain".desc).limit(nr))
        .getOrElse(ds)

    def sort(ds: Dataset[Regulation]) = ds.sort($"gain".desc)

    Some(expressionsByGene)
      .map(targetsOnly)
      .map(infer)
      .map(regularize)
      .map(normalize)
      .map(truncate)
      .map(sort)
      .get
  }

  /**
    * @param expressionsByGene The Dataset.
    * @param candidateRegulators The Set of candidate regulators.
    *
    * @return Returns a tuple of broadcast variables.
    */
  def createRegulatorBroadcasts(expressionsByGene: Dataset[ExpressionByGene], candidateRegulators: Set[Gene]):
      (Broadcast[List[Gene]], Broadcast[CSCMatrix[Expression]]) = {

    val sc = expressionsByGene.sparkSession.sparkContext

    val regulators = expressionsByGene.genes.filter(candidateRegulators.contains)

    assert(regulators.nonEmpty, s"no regulators w.r.t. specified candidate regulators ${candidateRegulators.take(3).mkString(",")}...")

    val regulatorCSC = reduceToRegulatorCSCMatrix(expressionsByGene, regulators)

    println(s"Estimated size of regulator matrix broadcast variable: ${SizeEstimator.estimate(regulatorCSC)} bytes")

    (sc.broadcast(regulators), sc.broadcast(regulatorCSC))
  }

  /**
    * @return Returns a multi-line String containing a human readable report of the inference run.
    */
  def makeReport(started: DateTime,
                 inferenceConfig: XGBoostConfig,
                 regulatorCSC: Option[CSCMatrix[Expression]],
                 params: XGBoostRegressionParams): String = {

    val finished = now
    val format = DateTimeFormat.forPattern("yyyy-MM-dd:hh.mm.ss")
    val startedPretty  = format.print(started)
    val finishedPretty = format.print(finished)

    val cscSize = regulatorCSC.map(csc => s"${SizeEstimator.estimate(csc)} bytes").getOrElse("not measured")

    s"""
      |# $GRN_BOOST run log
      |
      |* Started: $startedPretty, finished: $finishedPretty, diff: ${pretty(diff(started, finished))}
      |
      |* CSC broadcast size estimation: $cscSize.
      |
      |* Inference configuration:
      |${inferenceConfig.toString}
      |
      |* Regression params:
      |${params.toString}
    """.stripMargin
  }

  /**
    * Write the specified report to the stdout console and possibly to file.
    * Writes the Seq of cell indices (sub-sample) if not empty to file.
    *
    * @param spark The Spark Session
    * @param output The output path for the inference result, folder name is used with a suffix for the file report.
    * @param report The human readable report String.
    * @param reportToFile Boolean indicator that specifies whether reports should be written to file.
    */
  def writeReports(spark: SparkSession,
                   output: Path,
                   report: String,
                   reportToFile: Boolean = true): Unit = {

    out.println(report)

    if (reportToFile) {
      spark
        .sparkContext
        .parallelize(report.split("\n"))
        .repartition(1)
        .saveAsTextFile(reportOutput(output))
    }
  }

  private[grnboost] def resultOutput(output: Path) = s"$output.result"

  private[grnboost] def reportOutput(output: Path) = s"$output.report"

  /**
    * Move outputs from the Hadoop conventional directory structure to files if possible, fails silently.
    * Note: this procedure uses FileUtils, which doesn't work on S3, therefore we fail gracefully.
    *
    * @param output The output directory
    */
  private[grnboost] def moveOutputs(output: Path): Unit =
    Seq(
      (resultOutput(output), "tsv"),
      (reportOutput(output), "txt"))
      .foreach{ case (dir, extension) => try {
        copyFile(new File(dir, "part-00000"), new File(s"$dir.$extension"), false)

        deleteDirectory(new File(dir))
      } catch {
        case _: Exception => log.warn("could not ")
      }}

  /**
    * @param estimations The Dataset of RoundsEstimation instances
    * @param agg The aggregation function. Default = max.
    * @return Returns the final estimate of the nr of boosting rounds as a Try Option.
    */
  def aggregateEstimate(estimations: Dataset[RoundsEstimation],
                        agg: Column => Column = max): Option[Int] = {

    import estimations.sparkSession.implicits._

    Try {
      estimations
        .select(agg($"rounds"))
        .first
        .getInt(0)
    }.toOption
  }

  /**
    * Alternative template function that works with batch-iterated XGBoost matrices instead of copied anc cached ones.
    * A factory function creates the task executed in each flatMap step.
    *
    * @return Returns a Dataset of generic type equal to the generic type of the mapTaskFactory.
    */
  def computeMapped[T : Encoder : ClassTag](expressionsByGene: Dataset[ExpressionByGene],
                                            regulatorsBroadcast: Broadcast[List[Gene]],
                                            regulatorCSCBroadcast: Broadcast[CSCMatrix[Expression]],
                                            nrPartitions: Option[Count] = None)
                                           (mapTaskFactory: (List[Gene], CSCMatrix[Expression]) => Task[T]): Dataset[T] = {

    def repartition(ds: Dataset[ExpressionByGene]) =
      nrPartitions
        .map(ds.repartition(_).cache)
        .getOrElse(ds)

    def mapTask(ds: Dataset[ExpressionByGene]) =
      ds
        .flatMap(expressionByGene => {
          val task =
            mapTaskFactory(
              regulatorsBroadcast.value,
              regulatorCSCBroadcast.value)

          task.apply(expressionByGene)
        })

    Some(expressionsByGene)
      .map(repartition)
      .map(mapTask)
      .get
  }

  /**
    * Template function that breaks op the inference problem in partition-local iterator transformations in order to
    * keep a handle on cached regulation matrices. A factory function creates the task executed in each iterator step.
    *
    * @return Returns a Dataset of generic type equal to the generic type of the partitionTaskFactory.
    */
  def computePartitioned[T : Encoder : ClassTag](expressionsByGene: Dataset[ExpressionByGene],
                                                 regulatorsBroadcast: Broadcast[List[Gene]],
                                                 regulatorCSCBroadcast: Broadcast[CSCMatrix[Expression]],
                                                 nrPartitions: Option[Count])
                                                (partitionTaskFactory: (List[Gene], CSCMatrix[Expression], Partition) => PartitionTask[T]): Dataset[T] = {

    import expressionsByGene.sparkSession.implicits._

    def repartition(rdd: RDD[ExpressionByGene]) =
      nrPartitions
        .map(rdd.repartition(_).cache)
        .getOrElse(rdd)

    def mapPartitionTask(rdd: RDD[ExpressionByGene]) =
      rdd
        .mapPartitionsWithIndex{ case (partitionIndex, partitionIterator) => {
          if (partitionIterator.nonEmpty) {
            val partitionTask =
              partitionTaskFactory(
                regulatorsBroadcast.value,
                regulatorCSCBroadcast.value,
                partitionIndex)

            partitionIterator
              .flatMap{ expressionByGene => {
                val results = partitionTask.apply(expressionByGene)

                if (partitionIterator.isEmpty) {
                  partitionTask.dispose()
                }

                results
              }}
          } else
            Nil.iterator.asInstanceOf[Iterator[T]]
        }}

    Some(expressionsByGene)
      .map(_.rdd)
      .map(repartition)
      .map(mapPartitionTask)
      .map(_.toDS)
      .get
  }

  /**
    * GRNBoost assumes that the data will contain a substantial amount of zeros, motivating the use of a CSC sparse
    * matrix as the data structure that will be broadcast to the workers.
    *
    * @param expressionsByGene The Dataset of ExpressionByGene instances.
    * @param regulators The ordered List of regulators.
    *
    * @return Returns a CSCMatrix of regulator gene expression values.
    */
  def reduceToRegulatorCSCMatrix(expressionsByGene: Dataset[ExpressionByGene],
                                 regulators: List[Gene]): CSCMatrix[Expression] = {
    val nrGenes = regulators.size
    val nrCells = expressionsByGene.first.values.size

    val regulatorIndexMap       = regulators.zipWithIndex.toMap
    def isPredictor(gene: Gene) = regulatorIndexMap.contains(gene)
    def cscIndex(gene: Gene)    = regulatorIndexMap.apply(gene)

    expressionsByGene
      .rdd
      .filter(e => isPredictor(e.gene))
      .mapPartitions{ it =>
        val matrixBuilder = new CSCMatrix.Builder[Expression](rows = nrCells, cols = nrGenes)

        it.foreach { case ExpressionByGene(gene, expression) =>

          val geneIdx = cscIndex(gene)

          expression
            .foreachActive{ (cellIdx, value) =>
              matrixBuilder.add(cellIdx, geneIdx, value.toFloat)
            }
        }

        Iterator(matrixBuilder.result)
      }
      .treeReduce(_ + _) // https://issues.apache.org/jira/browse/SPARK-2174
  }

}

/**
  * Exposes the API method relevant to the computeMapped function
  *
  * @tparam T Generic result type.
  */
trait Task[T] {

  /**
    * @param expressionByGene The current target gene and its expression vector.
    * @return Returns a resulting iterable of Dataset entries.
    */
  def apply(expressionByGene: ExpressionByGene): Iterable[T]

}

/**
  * Exposes the two API methods relevant to the computePartitioned function.
  *
  * @tparam T Generic result type.
  */
trait PartitionTask[T] extends Task[T] {

  /**
    * Dispose used resources.
    */
  def dispose(): Unit

}