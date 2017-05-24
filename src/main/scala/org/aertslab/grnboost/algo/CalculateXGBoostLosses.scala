package org.aertslab.grnboost.algo

import breeze.linalg.CSCMatrix
import org.aertslab.grnboost._

/**
  * @author Thomas Moerman
  */
abstract case class CalculateXGBoostLosses(params: XGBoostRegressionParams)
                                 (regulators: List[Gene],
                                  regulatorCSC: CSCMatrix[Expression],
                                  partitionIndex: Int) extends PartitionTask[Loss] {



}