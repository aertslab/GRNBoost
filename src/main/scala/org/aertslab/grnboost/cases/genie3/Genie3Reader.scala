package org.aertslab.grnboost.cases.genie3

import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.aertslab.grnboost.cases.DataReader

import org.aertslab.grnboost._

/**
  * @author Thomas Moerman
  */
object Genie3Reader {

  /**
    * @param spark The SparkSession.
    * @param file The Genie3 expression file name.
    * @return Returns a tuple:
    *         - DataFrame
    *         - Gene list
    */
  def apply(spark: SparkSession, file: String): (DataFrame, List[String]) = {
    val csv =
      spark
        .read
        .option("header", true)
        .option("inferSchema", true)
        .option("delimiter", "\t")
        .csv(file)

    val assembler =
      new VectorAssembler()
        .setInputCols(csv.columns)
        .setOutputCol(EXPRESSION)

    val df = assembler.transform(csv).select(EXPRESSION)

    (df, csv.columns.toList)
  }

}