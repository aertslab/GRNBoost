package org.aertslab.grnboost.util

import breeze.linalg.{CSCMatrix, SparseVector}
import breeze.storage.Zero
import ml.dmlc.xgboost4j.java.DMatrix.SparseType.CSC
import ml.dmlc.xgboost4j.scala.DMatrix
import org.aertslab.grnboost.Expression

import scala.reflect.ClassTag

/**
  * @author Thomas Moerman
  */
object BreezeUtils {

  /**
    * @param csc A Breeze CSCMatrix.
    * @return Returns an XGBoost DMatrix.
    */
  def toDMatrix(csc: CSCMatrix[Expression]): DMatrix =
    new DMatrix(csc.colPtrs.map(_.toLong), csc.rowIndices, csc.data, CSC, csc.rows)

  /**
    * @param csc The CSCMatrix to pimp
    * @tparam T Generic numerical type
    * @return Returns a pimped CSCMatrix.
    */
  implicit def pimp[@specialized(Double, Int, Float, Long) T:ClassTag:Zero](csc: CSCMatrix[T]): CSCMatrixFunctions[T] =
    new CSCMatrixFunctions[T](csc)

}

class CSCMatrixFunctions[@specialized(Double, Int, Float, Long) T:ClassTag:Zero](m: CSCMatrix[T]) {

  /**
    * @param colIdx
    * @return
    */
  def getColumn(colIdx: Int): SparseVector[T] = {
    assert(colIdx < m.cols, s"Cannot get col $colIdx from CSCMatrix with ${m.cols} columns")



    //new SparseVector[T]()
    ???
  }

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