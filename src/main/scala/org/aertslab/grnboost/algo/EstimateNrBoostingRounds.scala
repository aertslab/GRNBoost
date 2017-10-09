package org.aertslab.grnboost.algo

import java.lang.Math.min
import java.util

import breeze.linalg.CSCMatrix
import ml.dmlc.xgboost4j.java.CVPack
import ml.dmlc.xgboost4j.java.GRNBoostExtras._
import ml.dmlc.xgboost4j.scala.DMatrix
import ml.dmlc.xgboost4j.scala.XGBoostConversions._
import org.aertslab.grnboost._
import org.aertslab.grnboost.algo.EstimateNrBoostingRounds._
import org.aertslab.grnboost.algo.TriangleRegularization.inflectionPointIndex
import org.aertslab.grnboost.util.BreezeUtils._

/**
  * Implementation where the XGBoost DMatrix is constructed with a batch iterator instead of copying the
  * CSC arrays. This approach is slower but has better memory usage characteristics.
  *
  * @param params The regression parameters.
  * @param regulators The list of regulator genes (transcription factors).
  * @param regulatorCSC The CSC expression matrix from which to distill
  */
case class EstimateNrBoostingRoundsIterated(params: XGBoostRegressionParams)
                                           (regulators: List[Gene],
                                            regulatorCSC: CSCMatrix[Expression]) extends Task[RoundsEstimation] {
  import params._

  def apply(expressionByGene: ExpressionByGene): Iterable[RoundsEstimation] = {

    val targetGene        = expressionByGene.gene
    val targetIsRegulator = regulators.contains(targetGene)

    log.debug(s"estimating nr boosting rounds -> target: $targetGene \t regulator: $targetIsRegulator")

    Some(regulatorCSC)
      .map(csc =>
        if (targetIsRegulator) {
          val targetColumnIndex = regulators.zipWithIndex.find(_._1 == targetGene).get._2
          csc.dropColumn(targetColumnIndex)
        } else {
          csc
        })
      .map(_.iterateToLabeledDMatrix(expressionByGene.response))
      .map(labeledMatrix => {
        val foldIndices = indicesByFold(nrFolds, regulatorCSC.rows, seed)

        val result = estimateBoostingRounds(nrFolds, targetGene, params, labeledMatrix, foldIndices)

        labeledMatrix.delete()

        result.toIterable
      })
      .get
  }

}

/**
  * A Partition Task for estimating the number of boosting rounds in function of a chosen XGBoost learning rate (eta).
  *
  * The estimating strategy is to calculate cross-validation performance on a test subset of the data and to find a
  * reasonable inflection point. The inflection point is the position where the test curve flattens out, meaning that
  * additional boosting rounds do not add any more value.
  *
  * The inflection point is found in a lazy fashion, where increasing boosting rounds are evaluated on the presence of
  * an inflection point.
  *
  * Deprecated because of a problem with dmatrix.slice when the matrix is large (happens with macosko but not with dream5).
  *
  * @param params The regression parameters.
  * @param regulators The list of regulator genes (transcription factors).
  * @param regulatorCSC The CSC expression matrix from which to distill
  * @param partition The index of the Spark partition.
  */
@deprecated
case class EstimateNrBoostingRounds(params: XGBoostRegressionParams)
                                   (regulators: List[Gene],
                                    regulatorCSC: CSCMatrix[Expression],
                                    partition: Partition) extends PartitionTask[RoundsEstimation] {
  import params._

  private[this] val cachedRegulatorDMatrix = regulatorCSC.copyToUnlabeledDMatrix

  override def apply(expressionByGene: ExpressionByGene): Iterable[RoundsEstimation] = {
    val targetGene        = expressionByGene.gene
    val targetIsRegulator = regulators.contains(targetGene)

    log.debug(s"estimating nr boosting rounds -> target: $targetGene \t regulator: $targetIsRegulator \t partition: $partition")

    val foldIndices = indicesByFold(nrFolds, regulatorCSC.rows, seed)

    if (targetIsRegulator) {
      val targetColumnIndex = regulators.zipWithIndex.find(_._1 == targetGene).get._2
      val cleanRegulatorDMatrix = regulatorCSC.dropColumn(targetColumnIndex).copyToUnlabeledDMatrix

      cleanRegulatorDMatrix.setLabel(expressionByGene.response) // !! Side effect !!

      val result = estimateBoostingRounds(nrFolds, targetGene, params, cleanRegulatorDMatrix, foldIndices)

      cleanRegulatorDMatrix.delete()

      result
    } else {
      cachedRegulatorDMatrix.setLabel(expressionByGene.response) // !! Side effect !!

      val result = estimateBoostingRounds(nrFolds, targetGene, params, cachedRegulatorDMatrix, foldIndices)

      result
    }
  }

  override def dispose(): Unit = {
    cachedRegulatorDMatrix.delete()
  }

}

/**
  * Pure functions factored out for testing and elegant composition purposes.
  *
  * @author Thomas Moerman
  */
object EstimateNrBoostingRounds {

  val MAX_ROUNDS  = 5000
  val INC_ROUNDS  = 50

  /**
    * @param nrFolds The nr of CV folds.
    * @param targetGene The target gene.
    * @param params The regression parameters.
    * @param regulatorDMatrix The DMatrix of regulator expression values.
    * @param indicesByFold A Map of cell indices by fold nr.
    * @param allCVSets If true, make CV sets with respect to all folds, otherwise only one CV set is constructed in
    *                  function of the fold slices.
    * @param maxRounds The maximum nr of boosting rounds to try.
    * @param incRounds The increment of boosting rounds for lazily finding the inflection point.
    *
    * @return Returns a Seq of RoundsEstimation instances.
    */
  def estimateBoostingRounds(nrFolds: Int,
                             targetGene: Gene,
                             params: XGBoostRegressionParams,
                             regulatorDMatrix: DMatrix,
                             indicesByFold: Map[FoldNr, List[CellIndex]],
                             allCVSets: Boolean = false,
                             maxRounds: Int = MAX_ROUNDS,
                             incRounds: Int = INC_ROUNDS): Option[RoundsEstimation] = {

    import scala.collection.JavaConverters._

    val (train, test) = cvSet(0, indicesByFold, regulatorDMatrix)

    val cvPack = new CVPack(train, test, params.boosterParams)

    val metric = params.boosterParams.getOrElse(XGB_METRIC, DEFAULT_EVAL_METRIC).toString

    val predicate = new Predicate[Option[RoundsEstimation]] {

      override def apply(evalHist: util.List[String]): Option[RoundsEstimation] = {
        val rounds = (0 until evalHist.size)

        val lossScores =
          evalHist
            .asScala
            .toList
            .map(parseLossScores(_, metric))

        val lossesByRound = (rounds zip lossScores).toArray

        val testLosses = lossScores.map(_._2)

        val idx = inflectionPointIndex(testLosses)

        idx
          .map(lossesByRound(_))
          .map{ case (round, (_, testLoss)) => RoundsEstimation(0, targetGene, testLoss, round) }
      }

      override def isDefined(opt: Option[RoundsEstimation]): Boolean = opt.isDefined

    }

    val estimation = updateWhile(cvPack, maxRounds, incRounds, predicate)

    cvPack.dispose()

    estimation
  }

  /**
    * @param modelEvaluation A String containing the booster model evaluation.
    * @return Returns the train and test loss scores, parsed from the model evaluation String.
    */
  def parseLossScores(modelEvaluation: String, evalMetric: String): (Loss, Loss) = {
    val losses =
      modelEvaluation
        .split("\t")
        .drop(1)
        .map(_.split(":") match {
          case Array(key, value) => (key, value.toFloat)
        })
        .toMap

    (losses(s"train-$evalMetric"), losses(s"test-$evalMetric"))
  }

  /**
    * @param foldNr The current fold nr.
    * @param indicesByFold The cell indices for each fold, by fold nr.
    * @param matrix The DMatrix to slice into training and test matrices.
    * @return Returns a pair of Arrays of cell indices that represent the cell IDs of one CV set.
    *         The fold slice with nr equal to foldNr becomes the test matrix, whereas the rest of the slices
    *         are used for the training matrix.
    */
  def cvSet(foldNr: FoldNr,
            indicesByFold: Map[FoldNr, List[CellIndex]],
            matrix: DMatrix): (DMatrix, DMatrix) = {

    val (trainSlices, testSlice) = indicesByFold.partition(_._1 != foldNr)

    val trainIndices = trainSlices.values.flatten.toArray
    val testIndices  = testSlice.values.flatten.toArray

    (matrix.slice(trainIndices), matrix.slice(testIndices))
  }

  /**
    * @param nrFolds The nr of folds.
    * @param nrSamples The nr of samples to slice into folds.
    * @param seed A seed for the random number generator.
    * @return Returns a Map of Lists of cell indices by fold id.
    */
  def indicesByFold(nrFolds: Count,
                    nrSamples: Count,
                    seed: Seed = DEFAULT_SEED): Map[FoldNr, List[CellIndex]] = {

    assert(nrFolds   > 1, s"nr folds must be greater than 1 (specified: $nrFolds)")
    assert(nrSamples > 0, s"nr samples must be greater than 0 (specified: $nrSamples)")

    val denominator = min(nrFolds, nrSamples)

    random(seed)
      .shuffle((0 until nrSamples).toList)
      .zipWithIndex
      .map{ case (cellIndex, idx) => (cellIndex, idx % denominator) }
      .groupBy{ case (_, fold) => fold }
      .mapValues(_.map(_._1).sorted)
  }

}