package org.apache.spark.ml.linalg

import breeze.linalg.{CSCMatrix, DenseMatrix => BDM, Matrix => BreezeMatrix, SparseVector => BSV, Vector => BreezeVector, DenseVector => BDV}
import breeze.storage.Zero
import org.apache.spark.ml.linalg.{Matrix => MLMatrix, Vector => MLVector}

import scala.reflect.ClassTag

/**
  * @author Thomas Moerman
  */
object BreezeMLConversions {

  implicit class MLLibVectorConversion(val vector: MLVector) extends AnyVal {
    def br: BreezeVector[Double] = vector.asBreeze
  }

  implicit class BreezeVectorConversion(val vector: BreezeVector[Double]) extends AnyVal {
    def ml: MLVector = Vectors.fromBreeze(vector)
  }
  
  implicit class MLLibMatrixConversion(val matrix: MLMatrix) extends AnyVal {
    def br: BreezeMatrix[Double] = matrix.asBreeze
  }

  implicit class BreezeMatrixConversion(val matrix: BreezeMatrix[Double]) extends AnyVal {
    def ml: MLMatrix = Matrices.fromBreeze(matrix)
  }

  implicit class DenseMatrixFunctions[T:ClassTag:Zero](val dense: BDM[T]) {

    def columns: Iterator[BDV[T]] = Iterator.tabulate(dense.cols) { j =>
      new BDV(dense.data.slice(j * dense.rows, (j + 1) * dense.rows))
    }

  }

  implicit class CSCMatrixFunctions[T:ClassTag:Zero](val csc: CSCMatrix[T]) {

    def columns: Iterator[BSV[T]] = Iterator.tabulate(csc.cols) { j =>
      val colStart = csc.colPtrs(j)
      val colEnd = csc.colPtrs(j + 1)

      val indices = csc.rowIndices.slice(colStart, colEnd)
      val values  = csc.data.slice(colStart, colEnd)

      val tuples = indices zip values

      BSV(csc.rows)(tuples: _*)
    }

  }

}