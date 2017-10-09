package org.aertslab

import java.io.File

import org.aertslab.grnboost.algo.TriangleRegularization._
import org.apache.commons.io.FileUtils.{copyFile, deleteDirectory}
import org.apache.log4j.Logger
import org.apache.log4j.Logger.getLogger
import org.apache.spark.ml.feature.VectorSlicer
import org.apache.spark.ml.linalg.{Vector => MLVector}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.FloatType
import org.apache.spark.sql.{Column, Dataset}

import scala.util.Random

/**
  * Application wide domain classes, constants and type aliases.
  *
  * @author Thomas Moerman
  */
package object grnboost {

  val GRN_BOOST = "GRNBoost"
  val URL       = "https://github.com/aertslab/GRNBoost/"

  type Path = String

  type Count = Int
  type Index = Long
  type Gene  = String

  type BoosterParams = Map[String, Any]

  type CellIndex = Int
  type CellCount = Int
  type GeneIndex = Int
  type GeneCount = Int
  type Round     = Int
  type Seed      = Int
  type FoldNr    = Int
  type Partition = Int

  type Expression = Float
  type Importance = Float
  type Loss       = Float

  type Frequency = Int
  type Gain      = Float
  type Cover     = Float

  type TreeDump  = String
  type ModelDump = Seq[TreeDump]

  val VALUES      = "values"
  val GENE        = "gene"
  val EXPRESSION  = "expression"
  val REGULATORS  = "regulators"
  val TARGET_GENE = "target_gene"

  val TARGET_INDEX    = "target_index"
  val TARGET_NAME     = "target_name"
  val REGULATOR_INDEX = "regulator_index"
  val REGULATOR_NAME  = "regulator_name"
  val IMPORTANCE      = "importance"

  val DEFAULT_MAX_BOOSTING_ROUNDS = 5000

  val DEFAULT_ESTIMATION_SET = 20
  val DEFAULT_NR_FOLDS       = 5
  val DEFAULT_NR_TRIALS      = 1000L
  val DEFAULT_SEED           = 666
  val DEFAULT_EVAL_METRIC    = "rmse"

  val XGB_THREADS   = "nthread"
  val XGB_SILENT    = "silent"
  val XGB_ETA       = "eta"
  val XGB_SEED      = "seed"
  val XGB_METRIC    = "eval_metric"
  val XGB_MAX_DEPTH = "max_depth"

  @transient val log: Logger = getLogger(GRN_BOOST)

  val DEFAULT_BOOSTER_PARAMS: BoosterParams = Map(
    XGB_SILENT    -> 1,
    XGB_THREADS   -> 1,
    XGB_ETA       -> 0.01,
    XGB_MAX_DEPTH -> 1
  )

  /**
    * Pimp-my-class addon functions.
    * @param boosterParams The implicit instance to wrap.
    */
  implicit class BoosterParamsFunctions(boosterParams: BoosterParams) {

    def withDefaults: BoosterParams =
      DEFAULT_BOOSTER_PARAMS
        .foldLeft(boosterParams){ case (params, (k, v)) =>
          if (params contains k) params else params updated (k, v) }

    def withSeed(seed: Seed): BoosterParams =
      boosterParams.updated("seed", seed)

  }

  /**
    * @param gene The gene name.
    * @param values The sparse expression vector.
    */
  case class ExpressionByGene(gene: Gene, values: MLVector) {
    def response: Array[Expression] = values.toArray.map(_.toFloat)
  }

  /**
    * Implicit pimp class for adding functions to Dataset[ExpressionByGene]
    * @param ds The Dataset of ExpressionByGene instances to pimp.
    */
  implicit class ExpressionByGeneDatasetFunctions(val ds: Dataset[ExpressionByGene]) {
    import ds.sparkSession.implicits._

    /**
      * @return Returns the genes in the Dataset as List of Strings.
      */
    def genes: List[Gene] = ds.select($"gene").rdd.map(_.getString(0)).collect.toList

    /**
      * @param cellIndices The cells to slice from the Dataset.
      * @return Returns the Dataset with values sliced in function of the specified Seq of cell indices.
      */
    def slice(cellIndices: Seq[CellIndex]): Dataset[ExpressionByGene] =
      new VectorSlicer()
        .setInputCol("values")
        .setOutputCol("sliced")
        .setIndices(cellIndices.toArray)
        .transform(ds)
        .select($"gene", $"sliced".as("values"))
        .as[ExpressionByGene]

    /**
      * Create a new Dataset, with only the expression values per gene for a subset of cells, of specified size.
      * The expression vectors in the new Dataset will have length equal to the sample size.
      *
      * @param nrCells The nr of cells to take into account in the sub-sampled Dataset.
      * @return Returns the cell indices of the sub-sample and the resulting Dataset.
      */
    def subSample(nrCells: Int): (Seq[CellIndex], Dataset[ExpressionByGene]) = {
      val count = ds.head.values.size

      if (nrCells >= count) (Nil, ds)
      else {
        val subset = randomSubset(nrCells, 0 until count)

        (subset, ds.slice(subset))
      }
    }

  }

  /**
    * Raw XGBoost regression output data structure.
    *
    * @param regulator The regulating gene.
    * @param target The target gene.
    * @param gain The gain score.
    * @param include Flag to indicate whether the link passed the regularization filter.
    */
  case class Regulation(regulator: Gene,
                        target: Gene,
                        gain: Gain,
                        include: Int = 1) {

    def mkString(d: String = "\t"): String = productIterator.mkString(d)

  }

  /**
    * @param foldNr The fold nr.
    * @param target The target gene.
    * @param loss The loss value.
    * @param rounds The number of rounds.
    */
  case class RoundsEstimation(@deprecated("unused for now") foldNr: FoldNr,
                              target: Gene,
                              loss: Loss,
                              rounds: Int)

  /**
    * Training and test loss by boosting round.
    *
    * @param target The target gene.
    * @param train The training loss.
    * @param test The test loss.
    * @param round The boosting round.
    */
  case class LossByRound(target: Gene, train: Loss, test: Loss, round: Int) {

    def mkString(d: String = "\t"): String = productIterator.mkString(d)

  }

  /**
    * Not part of the implicit pimp class because of Scala TypeChecker StackOverflow issues.
    *
    * @param params The regression parameters.
    * @return Returns the Dataset with regularization labels calculated with the Triangle method.
    */
  def withRegularizationLabels(ds: Dataset[Regulation], params: XGBoostRegressionParams): Dataset[Regulation] = {
    import ds.sparkSession.implicits._

    params
      .regularize
      .map(precision =>
        ds
          .rdd
          .groupBy(_.target)
          .values
          .flatMap(_.toList match {
            case Nil => Nil
            case list =>
              val sorted = list.sortBy(-_.gain)
              val gains  = sorted.map(_.gain)
              val result = (sorted zip labels(gains, precision)).map { case (reg, label) => reg.copy(include = label) }

              result
          })
          .toDS
      )
      .getOrElse(ds)
  }

  /**
    * @param ds The Dataset of Regulation entries.
    * @param agg Spark SQL aggregation function, default: sum.
    * @return Returns the Dataset, with gain scores normalized by an aggregation of the gain scores per target.
    */
  def normalizedByAggregate(ds: Dataset[Regulation], agg: Column => Column = sum): Dataset[Regulation] = {
    import ds.sparkSession.implicits._

    val aggImportanceByTarget =
      ds
        .groupBy($"target")
        .agg(agg($"gain").as("aggregation"))

    ds
      .join(aggImportanceByTarget, ds("target") === aggImportanceByTarget("target"), "inner")
      .withColumn("normalized", $"gain" / $"aggregation")
      .select(
        ds("regulator"),
        ds("target"),
        ds("include"),
        $"normalized".as("gain").cast(FloatType))
      .as[Regulation]
  }


  /**
    * Implicit pimp class.
    * @param ds The Dataset of Regulation instances.
    */
  implicit class RegulationDatasetFunctions(val ds: Dataset[Regulation]) {

    /**
      * Save the Dataset as a text file with specified delimiter
      *
      * @param out Target output file path.
      * @param includeFlags Include the label.
      * @param delimiter Default tab.
      */
    def saveTxt(out: Path,
                includeFlags: Boolean = true,
                delimiter: String = "\t"): Unit = {

      val nrCols = if (includeFlags) 4 else 3

      ds
        .rdd
        .map(_.productIterator.take(nrCols).mkString(delimiter))
        .repartition(1)
        .saveAsTextFile(out)
    }

  }

  sealed trait FeatureImportanceMetric
  case object GAIN  extends FeatureImportanceMetric
  case object COVER extends FeatureImportanceMetric
  case object FREQ  extends FeatureImportanceMetric

  /**
    * Data structure holding parameters for XGBoost regression.
    *
    * @param boosterParams The XGBoost Map of booster parameters.
    * @param nrRounds The nr of boosting rounds.
    * @param nrFolds The nr of folds in CV packs.
    * @param regularize Whether to use the L-curve cutoff strategy if Some. Contains threshold parameter.
    */
  case class XGBoostRegressionParams(boosterParams: BoosterParams = DEFAULT_BOOSTER_PARAMS,
                                     nrRounds: Option[Int] = None,
                                     nrFolds: Int = DEFAULT_NR_FOLDS,
                                     regularize: Option[Double] = Some(DEFAULT_PRECISION)) extends ConfigLike {

    def seed: Seed = boosterParams.get("seed").map(_.toString.toInt).getOrElse(DEFAULT_SEED)

  }

  /**
    * Early stopping parameter, for stopping boosting rounds when the delta in loss values is smaller than the
    * specified delta, over a window of boosting rounds of specified size. The boosting round halfway of the window
    * is returned as final result.
    *
    * @param size The size of the window.
    * @param lossDelta The loss delta over the window.
    */
  case class EarlyStopParams(size: Int = 10, lossDelta: Float = 0.01f)

  /**
    * @param seed The random seed.
    * @return Returns a new Random initialized with a seed.
    */
  def random(seed: Long): Random = {
    val rng = new Random(seed)
    rng.nextInt // get rid of first, low entropy
    rng
  }

  /**
    * @param keep The amount to keep from the range.
    * @param range The cell index range to choose from.
    * @param seed A random seed.
    * @return Returns the random subset.
    */
  def randomSubset(keep: Count, range: Range, seed: Seed = DEFAULT_SEED): Seq[CellIndex] = {
    val cellIndices: Seq[CellIndex] = range

    if (keep < range.size)
      random(seed).shuffle(cellIndices).take(keep).sorted
    else
      random(seed).shuffle(cellIndices).sorted
  }

  implicit class ProductFunctions(p: Product) {

    def toMap: Map[String, Any] =
      p
        .getClass.getDeclaredFields.map(_.getName)
        .zip(p.productIterator.to)
        .toMap

  }

}