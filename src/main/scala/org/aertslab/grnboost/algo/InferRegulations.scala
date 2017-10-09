package org.aertslab.grnboost.algo

import breeze.linalg.CSCMatrix
import ml.dmlc.xgboost4j.scala.{Booster, DMatrix, XGBoost}
import org.aertslab.grnboost._
import org.aertslab.grnboost.algo.InferRegulations._
import org.aertslab.grnboost.util.BreezeUtils._
import org.apache.spark.annotation.Experimental

/**
  * Implementation where the XGBoost DMatrix is constructed with a batch iterator instead of copying the
  * CSC arrays. This approach is slower but has better memory usage characteristics.
  *
  * @param params The regression parameters.
  * @param regulators The list of regulator genes (transcription factors).
  * @param regulatorCSC The CSC expression matrix from which to distill
  */
case class InferRegulationsIterated(params: XGBoostRegressionParams)
                                   (regulators: List[Gene],
                                    regulatorCSC: CSCMatrix[Expression]) extends Task[Regulation] {

  def apply(expressionByGene: ExpressionByGene): Iterable[Regulation] = {

    val targetGene        = expressionByGene.gene
    val targetIsRegulator = regulators.contains(targetGene)

    log.debug(s"inferring regulations -> target: $targetGene \t regulator: $targetIsRegulator")

    val cleanRegulators = Some(regulators)
      .map(regs =>
        if (targetIsRegulator) {
          regs.filterNot(_ == targetGene)
        } else {
          regs
        })

    val cleanRegulatorDMatrix = Some(regulatorCSC)
      .map(csc =>
        if (targetIsRegulator) {
          val targetColumnIndex = regulators.zipWithIndex.find(_._1 == targetGene).get._2
          csc.dropColumn(targetColumnIndex)
        } else {
          csc
        })
      .map(_.iterateToLabeledDMatrix(expressionByGene.response))

    (cleanRegulators zip cleanRegulatorDMatrix)
      .headOption
      .map{ case (regs, matrix) => {
        val result = inferRegulations(params, targetGene, regs, matrix)

        matrix.delete()

        result
      }}
      .get
  }

}

/**
  * A Partition Task for inferring the regulations.
  *
  * @param params The regression parameters.
  * @param regulators The list of regulator genes (transcription factors).
  * @param regulatorCSC The CSC expression matrix from which to distill
  * @param partition The index of the Spark partition.
  */
case class InferRegulations(params: XGBoostRegressionParams)
                           (regulators: List[Gene],
                            regulatorCSC: CSCMatrix[Expression],
                            partition: Partition) extends PartitionTask[Regulation] {

  private[this] val cachedRegulatorDMatrix = regulatorCSC.copyToUnlabeledDMatrix

  override def apply(expressionByGene: ExpressionByGene): Iterable[Regulation] = {
    val targetGene        = expressionByGene.gene
    val targetIsRegulator = regulators.contains(targetGene)

    log.debug(s"inferring regulations -> target: $targetGene \t regulator: $targetIsRegulator \t partition: $partition")

    if (targetIsRegulator) {
      val targetColumnIndex = regulators.zipWithIndex.find(_._1 == targetGene).get._2
      val cleanRegulatorDMatrix = regulatorCSC.dropColumn(targetColumnIndex).copyToUnlabeledDMatrix
      val cleanRegulators = regulators.filterNot(_ == targetGene)

      cleanRegulatorDMatrix.setLabel(expressionByGene.response) // !! Side effect !!

      val result = inferRegulations(params, targetGene, cleanRegulators, cleanRegulatorDMatrix)

      cleanRegulatorDMatrix.delete()

      result
    } else {
      cachedRegulatorDMatrix.setLabel(expressionByGene.response) // !! Side effect !!

      val result = inferRegulations(params, targetGene, regulators, cachedRegulatorDMatrix)

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
object InferRegulations {

  /**
    * Train the XGBoost regression model and extract the importance scores as regulations from the trained booster model.
    *
    * @param params The regression parameters.
    * @param targetGene The target gene.
    * @param regulators The List of regulator genes (transcription factors).
    * @param regulatorDMatrix The DMatrix of regulator expression values.
    *
    * @return returns a Seq of Regulation instances.
    */
  def inferRegulations(params: XGBoostRegressionParams,
                       targetGene: Gene,
                       regulators: List[Gene],
                       regulatorDMatrix: DMatrix): Seq[Regulation] = {
    import params._

    val booster = XGBoost.train(regulatorDMatrix, boosterParams.withDefaults, nrRounds.get)

    val regulations = toRegulations(targetGene, regulators, booster, params)

    booster.dispose

    regulations
  }

  /**
    * Extract the importance scores from the trained booster model and compose a sequence of Regulations.
    *
    * @param targetGene The target gene.
    * @param regulators The List of regulator genes.
    * @param booster The booster instance from which to extract the scores.
    *
    * @return Returns the raw scores for regulation.
    */
  def toRegulations(targetGene: Gene,
                    regulators: List[Gene],
                    booster: Booster,
                    params: XGBoostRegressionParams): Seq[Regulation] = {

    val boosterModelDump = booster.getModelDump(withStats = true).toSeq

    val scoresByGene = aggregateImportanceScoresByGene(params)(boosterModelDump)

    scoresByGene
      .toSeq
      .map{ case (geneIndex, scores) =>
        Regulation(regulators(geneIndex), targetGene, scores.gain)
      }
  }

  /**
    * See Python implementation:
    *   https://github.com/dmlc/xgboost/blob/d943720883f0e70ce1fbce809e373908b47bd506/python-package/xgboost/core.py#L1078
    *
    * @param modelDump Trained booster or tree model dump.
    * @return Returns the feature importance metrics parsed from all trees (amount == nr boosting rounds) in the
    *         specified trained booster model.
    */
  def aggregateImportanceScoresByGene(params: XGBoostRegressionParams)(modelDump: ModelDump): Map[GeneIndex, Scores] =
    modelDump
      .flatMap(parseImportanceScores)
      .foldLeft(Map[GeneIndex, Scores]() withDefaultValue Scores.ZERO) { case (acc, (geneIndex, scores)) =>
        acc.updated(geneIndex, acc(geneIndex) |+| scores)
      }

  /**
    * @param treeDump The dump of the boosted trees to parse.
    * @return Returns the feature importance metrics parsed from one tree.
    */
  def parseImportanceScores(treeDump: TreeDump): Array[(GeneIndex, Scores)] =
    treeDump
      .split("\n")
      .flatMap(_.split("\\[") match {
        case Array(_) => Nil // leaf node, ignore
        case array =>
          array(1).split("\\]") match {
            case Array(left, right) =>
              val geneIndex = left.split("<")(0).substring(1).toInt

              val scores = Scores(
                frequency = 1,
                gain  = getValue("gain", right),
                cover = getValue("cover", right)
              )

              (geneIndex, scores) :: Nil
          }
      })

  private def getValue(key: String, string: String) =
    string.split(",").find(_.startsWith(key)).map(_.split("=")(1)).get.toFloat

}

/**
  * A "smart" tuple with plus-like operator.
  */
case class Scores(frequency: Frequency,
                  gain: Gain,
                  cover: Cover) {

  def |+| (that: Scores) = Scores(
    this.frequency + that.frequency,
    this.gain      + that.gain,
    this.cover     + that.cover
  )

}

object Scores {

  val ZERO = Scores(0, 0f, 0f)

}