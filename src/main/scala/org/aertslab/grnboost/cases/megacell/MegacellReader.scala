package org.aertslab.grnboost.cases.megacell

import java.lang.Math.min

import breeze.linalg.{CSCMatrix, SparseVector => BSV}
import ch.systemsx.cisd.hdf5.{HDF5FactoryProvider, IHDF5Reader}
import ml.dmlc.xgboost4j.java.DMatrix.SparseType.CSC
import ml.dmlc.xgboost4j.scala.DMatrix
import org.apache.spark.ml.attribute.AttributeGroup
import org.apache.spark.ml.linalg.BreezeMLConversions._
import org.apache.spark.ml.linalg.SparseVector
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.aertslab.grnboost._
import org.aertslab.grnboost.cases.DataReader._
import resource._

import scala.collection.JavaConversions._
import scala.util.Try

/**
  * Reads the CSC matrix from the Chromium Megacell file.
  *
  * Source:
  *  -> https://support.10xgenomics.com/single-cell/datasets/1M_neurons
  *  -> aggr - Gene / cell matrix HDF5 (filtered)	3.79 GB	c134b01776f4e7da81a24c0b0a45c4b6
  *
  * @author Thomas Moerman
  */
object MegacellReader {

  private[megacell] val MEGACELL_CELL_COUNT = 1300774
  private[megacell] val MEGACELL_GENE_COUNT = 27998
  private[megacell] val MEGACELL_REGULATOR_COUNT = 1607

  private[megacell] val INDPTR     = "/mm10/indptr"
  private[megacell] val DATA       = "/mm10/data"
  private[megacell] val INDICES    = "/mm10/indices"
  private[megacell] val SHAPE      = "/mm10/shape"
  private[megacell] val GENE_NAMES = "/mm10/gene_names"

  type ExpressionTuples = Array[(GeneIndex, Expression)]

  type RowFn[T] = (CellIndex, GeneCount, ExpressionTuples) => T

  class CandidateRegulatorVector(val candidateRegulatorIndices: Array[GeneIndex]) extends RowFn[BSV[Float]] {
    val indexMap = candidateRegulatorIndices.zipWithIndex.toMap
    val length = candidateRegulatorIndices.length

    def apply(cellIndex: CellIndex, geneCount: GeneCount, expressionTuples: ExpressionTuples) = {
      val reIndexedTuples = expressionTuples.map{ case (i, v) => (indexMap(i), v.toFloat) }

      BSV(length)(reIndexedTuples: _*)
    }
  }

  object IntSparseVector extends RowFn[BSV[Expression]] {
    def apply(cellIndex: CellIndex, geneCount: GeneCount, expressionTuples: ExpressionTuples) =
      BSV(geneCount)(expressionTuples: _*)
  }

  object FloatSparseVector extends RowFn[BSV[Float]] {
    def apply(cellIndex: CellIndex, geneCount: GeneCount, expressionTuples: ExpressionTuples) =
      BSV(geneCount)(expressionTuples.map { case (i, v) => (i, v.toFloat) }: _*)
  }

  object DoubleSparseVector extends RowFn[BSV[Double]] {
    def apply(cellIndex: CellIndex, geneCount: GeneCount, expressionTuples: ExpressionTuples) =
      BSV(geneCount)(expressionTuples.map { case (i, v) => (i, v.toDouble) }: _*)
  }

  /**
    * @param hdf5
    * @param rowFn
    * @param cellTop
    * @param genePredicate
    * @tparam R
    * @return
    */
  def readRows[R](hdf5: Path,
                  rowFn: RowFn[R],
                  cellTop: Option[CellCount] = None,
                  genePredicate: Option[GeneIndex => Boolean] = None): Try[List[R]] =
    managed(HDF5FactoryProvider.get.openForReading(hdf5))
      .map{ reader => readRows(reader, rowFn, cellTop, genePredicate) }
      .tried

  /**
    * @param reader
    * @param rowFn
    * @param cellTop
    * @param genePredicate
    * @tparam R
    * @return
    */
  def readRows[R](reader: IHDF5Reader,
                  rowFn: RowFn[R],
                  cellTop: Option[CellCount],
                  genePredicate: Option[GeneIndex => Boolean]): List[R] = {

    val (nrGenes, nrCells) = readDimensions(reader)

    val pointers = reader.int64.readArray(INDPTR)

    val take = cellTop.map(min(_, nrCells)).getOrElse(nrCells)

    List
      .tabulate(take){ cellIndex =>

        val colStart  = pointers(cellIndex)
        val colEnd    = pointers(cellIndex + 1)

        val expressionTuples = readRow(reader, colStart, colEnd)

        val filteredTuples = filterTuples(genePredicate, expressionTuples)

        rowFn.apply(cellIndex, nrGenes, filteredTuples)
      }
  }

  private[this] def filterTuples(genePredicate: Option[GeneIndex => Boolean], expressionTuples: ExpressionTuples) =
    genePredicate
      .map(pred => expressionTuples.filter{ case (i, v) => pred(i) })
      .getOrElse(expressionTuples)


  private[this] def readRow(reader: IHDF5Reader, offset: Long, next: Long): ExpressionTuples = {
    val blockSize = (next - offset).toInt

    val geneIndices = reader.int32.readArrayBlockWithOffset(INDICES, blockSize, offset)

    val expressionValues = reader.int32.readArrayBlockWithOffset(DATA, blockSize, offset).map(_.toFloat)

    geneIndices zip expressionValues
  }

  /**
    * @param hdf5 The file path.
    * @param cellTop Optional limit on how many cells to read from the file - for testing purposes.
    * @param onlyGeneIndices Optional selection of gene indices to keep, like a preemptive slicing operation.
    * @return Returns a CSCMatrix of Ints.
    */
  def readCSCMatrix(hdf5: Path,
                    cellTop: Option[CellCount] = None,
                    onlyGeneIndices: Option[Seq[GeneIndex]] = None): Try[CSCMatrix[Expression]] =
    managed(HDF5FactoryProvider.get.openForReading(hdf5))
      .map{ reader => readCSCMatrix(reader, cellTop, onlyGeneIndices) }
      .tried

  /**
    * @param reader The managed HDF5 Reader instance.
    * @param cellTop Optional limit on how many cells to read from the file - for testing purposes.
    * @param onlyGeneIndices Optional selection of gene indices to keep, like a preemptive slicing operation.
    * @return Returns a CSCMatrix of ExpressionValues.
    */
  def readCSCMatrix(reader: IHDF5Reader,
                    cellTop: Option[CellCount],
                    onlyGeneIndices: Option[Seq[GeneIndex]]): CSCMatrix[Expression] = {

    val (nrCells, nrGenes) = readDimensions(reader)

    val pointers = reader.int64.readArray(INDPTR)

    val cellDim = cellTop.map(min(_, nrCells)).getOrElse(nrCells)
    val geneDim = onlyGeneIndices.map(_.size).getOrElse(nrGenes)

    val matrixBuilder = new CSCMatrix.Builder[Expression](rows = cellDim, cols = geneDim)

    val genePredicate = onlyGeneIndices.map(_.toSet)
    val reindex: GeneIndex => GeneIndex = onlyGeneIndices.map(_.zipWithIndex.toMap).getOrElse(identity _)

    for (cellIndex <- 0 until cellDim) {
      val colStart  = pointers(cellIndex)
      val colEnd    = pointers(cellIndex + 1)
      val rowTuples = readRow(reader, colStart, colEnd)

      val filteredTuples = filterTuples(genePredicate, rowTuples)
      
      if (cellIndex % 100000 == 0) print('.')

      filteredTuples
        .foreach{ case (geneIndex, value) => matrixBuilder.add(cellIndex, reindex(geneIndex), value) }
    }

    matrixBuilder.result
  }

  /**
    * @param hdf5 The file path.
    * @return Returns tuple (cell count, gene count).
    */
  def readDimensions(hdf5: Path): Try[(CellCount, GeneCount)] =
    managed(HDF5FactoryProvider.get.openForReading(hdf5))
      .map(readDimensions)
      .tried

  /**
    * @param reader The managed HDF5 reader instance.
    * @return Returns tuple (cell count, gene count).
    */
  def readDimensions(reader: IHDF5Reader): (CellCount, GeneCount) = reader.int32.readArray(SHAPE) match {
    case Array(nrGenes, nrCells) => (nrCells, nrGenes)
    case _ => throw new Exception("Could not read shape.")
  }

  def readGeneNames(hdf5: Path): Try[List[Gene]] =
    managed(HDF5FactoryProvider.get.openForReading(hdf5))
      .map(readGeneNames)
      .tried

  def readGeneNames(reader: IHDF5Reader) = reader.string.readArray(GENE_NAMES).toList

  private[this] def ml(v: BSV[Expression]): SparseVector = {
    if (v.index.length == v.used) {
      new SparseVector(v.length, v.index, v.data.map(_.toDouble))
    } else {
      new SparseVector(v.length, v.index.slice(0, v.used), v.data.slice(0, v.used).map(_.toDouble))
    }
  }

  def toExpressionByGeneDataset(spark: SparkSession,
                                cscBlock: CSCMatrix[Expression],
                                genes: List[Gene]) = {
    import spark.implicits._

    (cscBlock.columns zip genes.toIterator)
      .map{ case (vector, gene) => ExpressionByGene(gene, ml(vector)) }
      .toSeq
      .toDS
  }

  @deprecated("prefer statically typed dataset")
  def toColumnDataFrame(spark: SparkSession,
                        cscBlock: CSCMatrix[Expression],
                        genes: List[Gene]): DataFrame = {
    val rows =
      (cscBlock.columns zip genes.toIterator)
        .map{ case (vector, gene) => Row.apply(ml(vector), gene) }

    val schema = StructType(
      new AttributeGroup(VALUES).toStructField ::
      new StructField(GENE, StringType) :: Nil)

    spark.createDataFrame(rows.toSeq, schema)
  }

  def toDMatrix(csc: CSCMatrix[Int]) =
    new DMatrix(csc.colPtrs.map(_.toLong), csc.rowIndices, csc.data.map(_.toFloat), CSC)

}