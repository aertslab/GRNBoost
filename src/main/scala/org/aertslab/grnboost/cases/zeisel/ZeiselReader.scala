package org.aertslab.grnboost.cases.zeisel

import org.apache.spark.ml.linalg.{Vectors, Vector => MLVector}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Dataset, SparkSession}
import org.aertslab.grnboost._

/**
  * Read the Zeisel mRNA expression data a Spark SQL DataFrame.
  * @author Thomas Moerman
  */
object ZeiselReader {

  /**
    * Type representing a Line of the Zeisel mRNA expression file.
    * The raw file has row ~ features, and cols ~ values per cell
    *
    * A Line consists of a feature and the values for that feature across cells.
    */
  type Line = (List[String], Index)

  val ZEISEL_CELL_COUNT = 3005
  val ZEISEL_GENE_COUNT = 19972
  
  private[zeisel] val NR_META_FEATURES  = 10
  private[zeisel] val EMPTY_LINE_INDEX  = NR_META_FEATURES

  /**
    * @param spark The SparkSession.
    * @param raw The raw Zeisel mRNAExpression file.
    * @return
    */
  def apply(spark: SparkSession, raw: Path): Dataset[ExpressionByGene] = {
    import spark.implicits._

    rawLines(spark, raw)
      .flatMap(toExpressionByGene)
      .toDS
  }

  /**
    * @param spark The SparkSession.
    * @param raw The raw Zeisel file path.
    * @return Returns the raw lines without the empty line between meta and expression data.
    */
  private[zeisel] def rawLines(spark: SparkSession, raw: Path): RDD[Line] =
    spark
      .sparkContext
      .textFile(raw)
      .zipWithIndex
      .map{ case (string, idx) =>
        val split = string.split("\t").map(_.trim).toList
        (split, idx) }
      .filter(_._2 != EMPTY_LINE_INDEX)

  private[zeisel] def toExpressionByGene(line: Line): Option[ExpressionByGene] =
    Some(line)
      .filter(_._2 >= NR_META_FEATURES)
      .map{
        case (gene :: _ :: values, _) => ExpressionByGene(gene, toExpressionVector(values))
        case _                        => ???
      }

  private[zeisel] def toExpressionVector(values: List[String]): MLVector = {
    val tuples =
      values
        .zipWithIndex
        .filterNot(_._1 == "0")
        .map { case (value, cellIdx) => (cellIdx, value.toDouble) }

    Vectors.sparse(values.length, tuples)
  }

}