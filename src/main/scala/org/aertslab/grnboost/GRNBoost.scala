package org.aertslab.grnboost

import breeze.linalg.CSCMatrix
import org.aertslab.grnboost.algo.{InferXGBoostRegulations, OptimizeXGBoostHyperParams}
import org.apache.spark.sql.{Dataset, Encoder}

import scala.reflect.ClassTag

/**
  * @author Thomas Moerman
  */
object GRNBoost {

  /**
    * @param expressionsByGene A Dataset of ExpressionByGene instances.
    * @param candidateRegulators The Set of candidate regulators (TF).
    *                            The term "candidate" is used to imply that not all these regulators are expected
    *                            to be present in the specified List of all genes.
    * @param targets A Set of target genes for which we wish to infer the important regulators.
    *                If empty Set is specified, this is interpreted as: target genes = all genes.
    * @param params The XGBoost regression parameters.
    * @param nrPartitions Optional technical parameter for defining the nr. of Spark partitions to use.
    *
    * @return Returns a Dataset of Regulation instances.
    */
  def inferRegulations(expressionsByGene: Dataset[ExpressionByGene],
                       candidateRegulators: Set[Gene],
                       targets: Set[Gene] = Set.empty,
                       params: XGBoostRegressionParams = XGBoostRegressionParams(),
                       nrPartitions: Option[Int] = None): Dataset[RawRegulation] = {

    import expressionsByGene.sparkSession.implicits._

    val partitionTaskFactory = InferXGBoostRegulations(params)(_, _, _)

    computePartitioned(expressionsByGene, candidateRegulators, targets, nrPartitions)(partitionTaskFactory)
  }

  /**
    * @param expressionsByGene A Dataset of ExpressionByGene instances.
    * @param candidateRegulators The Set of candidate regulators (TF).
    *                            The term "candidate" is used to imply that not all these regulators are expected
    *                            to be present in the specified List of all genes.
    * @param targets A Set of target genes for which we wish to infer the important regulators.
    *                If empty Set is specified, this is interpreted as: target genes = all genes.
    * @param params The XGBoost hyperparameter optimization parameters.
    * @param nrPartitions Optional technical parameter for defining the nr. of Spark partitions to use.
    *
    * @return Returns a Dataset of OptimizedHyperParams.
    */
  def optimizeHyperParams(expressionsByGene: Dataset[ExpressionByGene],
                          candidateRegulators: Set[Gene],
                          targets: Set[Gene] = Set.empty,
                          params: XGBoostOptimizationParams = XGBoostOptimizationParams(),
                          nrPartitions: Option[Int] = None): Dataset[HyperParamsLoss] = {

    import expressionsByGene.sparkSession.implicits._

    val partitionTaskFactory = OptimizeXGBoostHyperParams(params)(_, _, _)

    def multiplex(e: ExpressionByGene) = Iterable.tabulate(params.nrBatches)(_ => e)

    computePartitioned(expressionsByGene, candidateRegulators, targets, nrPartitions, Some(multiplex))(partitionTaskFactory)
  }

  def computePartitioned[T : Encoder : ClassTag](
    expressionsByGene: Dataset[ExpressionByGene],
    candidateRegulators: Set[Gene],
    targets: Set[Gene],
    nrPartitions: Option[Int],
    multiplexer: Option[(ExpressionByGene) => Iterable[ExpressionByGene]] = None)
   (partitionTaskFactory: (List[Gene], CSCMatrix[Expression], Int) => PartitionTask[T]): Dataset[T] = {

    val spark = expressionsByGene.sparkSession
    val sc = spark.sparkContext

    import spark.implicits._

    val regulators = expressionsByGene.genes.filter(candidateRegulators.contains)

    assert(regulators.nonEmpty,
      s"no regulators w.r.t. specified candidate regulators ${candidateRegulators.take(3).mkString(",")}...")

    val regulatorCSC = reduceToRegulatorCSCMatrix(expressionsByGene, regulators)

    val regulatorsBroadcast   = sc.broadcast(regulators)
    val regulatorCSCBroadcast = sc.broadcast(regulatorCSC)

    def isTarget(e: ExpressionByGene) = containedIn(targets)(e.gene)

    val onlyTargets =
      expressionsByGene
        .filter(isTarget _)
        .rdd

    val multiplexed =
      multiplexer
        .map(mplxr => onlyTargets.flatMap(x => mplxr(x)))
        .getOrElse(onlyTargets)

    val repartitioned =
      nrPartitions
        .map(multiplexed.repartition(_).cache)
        .getOrElse(multiplexed)

    repartitioned
      .mapPartitionsWithIndex{ case (partitionIndex, partitionIterator) => {
        if (partitionIterator.nonEmpty) {
          val regulators    = regulatorsBroadcast.value
          val regulatorCSC  = regulatorCSCBroadcast.value
          val partitionTask = partitionTaskFactory.apply(regulators, regulatorCSC, partitionIndex)

          partitionIterator
            .flatMap{ expressionByGene => {
              val results = partitionTask(expressionByGene)

              if (partitionIterator.isEmpty) {
                partitionTask.dispose()
              }

              results
            }}
        } else {
          Nil.iterator.asInstanceOf[Iterator[T]]
        }
      }}
      .toDS
  }

  private[grnboost] def containedIn(targets: Set[Gene]): Gene => Boolean =
    if (targets.isEmpty)
      _ => true
    else
      targets.contains

  /**
    * @param expressionByGene The Dataset of ExpressionByGene instances.
    * @param regulators The ordered List of regulators.
    *
    * @return Returns a CSCMatrix of regulator gene expression values.
    */
  def reduceToRegulatorCSCMatrix(expressionByGene: Dataset[ExpressionByGene],
                                 regulators: List[Gene]): CSCMatrix[Expression] = {

    val nrGenes = regulators.size
    val nrCells = expressionByGene.first.values.size

    val regulatorIndexMap       = regulators.zipWithIndex.toMap
    def isPredictor(gene: Gene) = regulatorIndexMap.contains(gene)
    def cscIndex(gene: Gene)    = regulatorIndexMap.apply(gene)

    expressionByGene
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
  * Exposes the two API methods relevant to the computePartitioned function.
  *
  * @tparam T Generic result type.
  */
trait PartitionTask[T] {

  /**
    * @return Returns a resulting iterable of Dataset entries.
    */
  def apply(expressionByGene: ExpressionByGene): Iterable[T]

  /**
    * Dispose used resources.
    */
  def dispose(): Unit

}