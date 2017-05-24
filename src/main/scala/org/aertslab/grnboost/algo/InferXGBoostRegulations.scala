package org.aertslab.grnboost.algo

import breeze.linalg.CSCMatrix
import ml.dmlc.xgboost4j.scala.{Booster, DMatrix, XGBoost}
import org.aertslab.grnboost._
import org.aertslab.grnboost.util.BreezeUtils._
import InferXGBoostRegulations._

/**
  * @author Thomas Moerman
  */
case class InferXGBoostRegulations(params: XGBoostRegressionParams)
                                  (regulators: List[Gene],
                                   regulatorCSC: CSCMatrix[Expression],
                                   partitionIndex: Int) extends PartitionTask[RawRegulation] {
  import params._

  private[this] val cachedRegulatorDMatrix = toDMatrix(regulatorCSC)

  /**
    * @param expressionByGene The current target gene and its expression vector.
    * @return Returns the inferred Regulation instances for one ExpressionByGene instance.
    */
  override def apply(expressionByGene: ExpressionByGene): Iterable[RawRegulation] = {
    val targetGene        = expressionByGene.gene
    val targetIsRegulator = regulators.contains(targetGene)

    println(s"-> target: $targetGene \t regulator: $targetIsRegulator \t partition: $partitionIndex")

    if (targetIsRegulator) {
      // drop the target gene column from the regulator CSC matrix and create a new DMatrix
      val targetColumnIndex = regulators.zipWithIndex.find(_._1 == targetGene).get._2
      val cleanRegulatorDMatrix = toDMatrix(regulatorCSC dropColumn targetColumnIndex)
      val cleanRegulators = regulators.filterNot(_ == targetGene)

      // set the response labels and train the model
      cleanRegulatorDMatrix.setLabel(expressionByGene.response)

      val result = inferRegulations(targetGene, cleanRegulators, cleanRegulatorDMatrix)

      cleanRegulatorDMatrix.delete()

      result
    } else {
      // set the response labels and train the model
      cachedRegulatorDMatrix.setLabel(expressionByGene.response)

      val result = inferRegulations(targetGene, regulators, cachedRegulatorDMatrix)

      result
    }
  }

  private def inferRegulations(targetGene: Gene,
                               regulators: List[Gene],
                               regulatorDMatrix: DMatrix): Seq[RawRegulation] = {

    if (showCV) {
      val cv = XGBoost.crossValidation(regulatorDMatrix, boosterParams.withDefaults, nrRounds, 10)

      val tuples =
        cv
          .map(_.split("\t").drop(1).map(_.split(":")(1).toFloat))
          .zipWithIndex
          .map{ case (Array(train, test), i) => (i, train, test) }
          .map(_.productIterator.mkString("\t"))

      println(tuples.mkString("\n"))
    }

    val booster = XGBoost.train(regulatorDMatrix, boosterParams.withDefaults, nrRounds)

    val regulations = toRawRegulations(targetGene, regulators, booster, params)

    booster.dispose

    regulations
  }

  /**
    * Dispose the cached DMatrix.
    */
  override def dispose(): Unit = {
    cachedRegulatorDMatrix.delete()
  }

}

object InferXGBoostRegulations {

  type TreeDump  = String
  type ModelDump = Seq[TreeDump]

  /**
    * @param targetGene
    * @param regulators
    * @param booster
    * @return Returns the raw scores for regulation.
    */
  def toRawRegulations(targetGene: Gene,
                       regulators: List[Gene],
                       booster: Booster,
                       params: XGBoostRegressionParams): Seq[RawRegulation] = {

    val boosterModelDump = booster.getModelDump(withStats = true).toSeq

    aggregateGainByGene(params)(boosterModelDump)
      .toSeq
      .map{ case (geneIndex, normalizedGain) =>
        RawRegulation(regulators(geneIndex), targetGene, normalizedGain)
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
  def aggregateGainByGene(params: XGBoostRegressionParams)(modelDump: ModelDump): Map[GeneIndex, Gain] =
    modelDump
      .flatMap(parseGainScores)
      .foldLeft(Map[GeneIndex, Gain]() withDefaultValue 0f) { case (acc, (geneIndex, gain)) =>
        acc.updated(geneIndex, acc(geneIndex) + gain)
      }

  /**
    * @param treeDump
    * @return Returns the feature importance metrics parsed from one tree.
    */
  def parseGainScores(treeDump: TreeDump): Array[(GeneIndex, Gain)] =
    treeDump
      .split("\n")
      .flatMap(_.split("\\[") match {
        case Array(_) => Nil // leaf node, ignore
        case array =>
          array(1).split("\\]") match {
            case Array(left, right) =>
              val geneIndex = left.split("<")(0).substring(1).toInt
              val gain     = right.split(",").find(_.startsWith("gain")).map(_.split("=")(1)).get.toFloat

              (geneIndex, gain) :: Nil
          }
      })
}