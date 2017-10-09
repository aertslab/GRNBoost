package org.aertslab.grnboost.util

import breeze.linalg.{CSCMatrix, SparseVector => BSV}
import breeze.storage.Zero
import ml.dmlc.xgboost4j.LabeledPoint
import ml.dmlc.xgboost4j.LabeledPoint.fromSparseVector
import ml.dmlc.xgboost4j.java.DMatrix.SparseType.CSC
import ml.dmlc.xgboost4j.scala.DMatrix
import org.aertslab.grnboost.Expression

import scala.collection.Iterator.tabulate
import scala.reflect.ClassTag

/**
  * @author Thomas Moerman
  */
object BreezeUtils {

  implicit def pimp1[@specialized(Double, Int, Float, Long) T:ClassTag:Zero](csc: CSCMatrix[T]): CSCMatrixFunctions[T] =
    new CSCMatrixFunctions[T](csc)

  implicit def pimp2(csc: CSCMatrix[Expression]): ExpressionCSCMatrixFunctions =
    new ExpressionCSCMatrixFunctions(csc)

}

/**
  * @param csc The sparse CSCMatrix, rows = cells, cols = genes
  */
class ExpressionCSCMatrixFunctions(csc: CSCMatrix[Expression]) {

  /**
    * Copies the CSC arrays to the XGBoost matrix.
    * @return Returns an XGBoost DMatrix
    */
  def copyToUnlabeledDMatrix: DMatrix =
    new DMatrix(csc.colPtrs.map(_.toLong), csc.rowIndices, csc.data, CSC, csc.rows)

  /**
    * Makes an iterated DMatrix in function of a LabeledPoint iterator.
    * @param labels The Array of label expression values.
    * @return Returns an XGBoost DMatrix with labels.
    */
  def iterateToLabeledDMatrix(labels: Array[Expression]): DMatrix =
    new DMatrix(labeledPointsIterator(labels))

  val m = csc.t

  /**
    * Lifted from Spark ML Matrices, modified to return a sparse vector of Float (Expression).
    * @return Returns an iterator of Sparse expression vectors.
    */
  def sparseVectorIterator: Iterator[BSV[Expression]] =
    tabulate(m.cols) { j =>
      val colStart = m.colPtrs(j)
      val colEnd   = m.colPtrs(j + 1)

      val ii = m.rowIndices.slice(colStart, colEnd)
      val vv = m.data.slice(colStart, colEnd)

      BSV(m.rows)(ii zip vv: _*)
    }

  /**
    * Inspired by above.
    * @param labels The Array of labels.
    * @return Returns an iterator of LabeledPoint instances.
    */
  def labeledPointsIterator(labels: Array[Expression]): Iterator[LabeledPoint] =
    tabulate(m.cols) { j =>
      val colStart = m.colPtrs(j)
      val colEnd   = m.colPtrs(j + 1)

      val ii = m.rowIndices.slice(colStart, colEnd)
      val vv = m.data.slice(colStart, colEnd)

      fromSparseVector(labels(j), ii, vv)
    }

}

class CSCMatrixFunctions[@specialized(Double, Int, Float, Long) T:ClassTag:Zero](m: CSCMatrix[T]) {

  /**
    * Efficient implementation of a specialized "slice", where only one column is removed from the CSCMatrix.
    *
    * @param colIdx Index of the column to drop from the CSCMatrix
    * @return Returns a new CSCMatrix without the column with specified index.
    */
  def dropColumn(colIdx: Int): CSCMatrix[T] = {
    assert(colIdx < m.cols, s"Cannot drop col $colIdx from CSCMatrix with ${m.cols} columns")

    val colPtr_L = m.colPtrs(colIdx)
    val colPrt_R = m.colPtrs(colIdx + 1)
    val colSize  = colPrt_R - colPtr_L

    val data2        = { val (l, r) = punch(m.data,       colPtr_L, colSize); l ++ r }
    val rowIndices2  = { val (l, r) = punch(m.rowIndices, colPtr_L, colSize); l ++ r }
    val colPointers2 = { val (l, r) = punch(m.colPtrs,    colIdx,      1);    l ++ r.map(_ - colSize) }

    new CSCMatrix[T](data2, m.rows, m.cols - 1, colPointers2, rowIndices2)
  }

  private[util] def punch[@specialized(Double, Int, Float, Long) T:ClassTag:Zero](a: Array[T], keep: Int, drop: Int) = {
    val (left, t) = a.splitAt(keep)
    val (_, rest) = t.splitAt(drop)

    (left, rest)
  }

}