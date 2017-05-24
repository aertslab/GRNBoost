package org.aertslab.grnboost.cases.dream5

import breeze.linalg._
import org.apache.spark.ml.linalg.BreezeMLConversions._
import org.apache.spark.sql.{Dataset, SparkSession}
import org.aertslab.grnboost.{ExpressionByGene, Gene}
import org.aertslab.grnboost.cases.DataReader._

import scala.io.Source

/**
  * Reader function for the Dream 5 training data.
  *
  * @author Thomas Moerman
  */
object Dream5Reader {

  /**
    * @param spark The SparkSession.
    * @param dataFile The data file.
    * @param tfFile The TF file.
    * @return Returns the expression matrix by gene Dataset and the List of TF genes.
    */
  def readTrainingData(spark: SparkSession,
                       dataFile: String,
                       tfFile: String,
                       delimiter: Char = '\t'): (Dataset[ExpressionByGene], List[Gene]) = {

    import spark.implicits._

    val TFs = Source.fromFile(tfFile).getLines.toList

    val genes = Source.fromFile(dataFile).getLines.next.split(delimiter)
    val matrix = csvread(dataFile, delimiter, skipLines = 1)
    val ds =
      (genes.toSeq zip matrix.t.ml.rowIter.toSeq)
        .map{ case (gene, expression) => ExpressionByGene(gene, expression) }
        .toDS
        .cache

    (ds, TFs)
  }

}