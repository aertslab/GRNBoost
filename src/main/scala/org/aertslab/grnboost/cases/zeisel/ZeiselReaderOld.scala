package org.aertslab.grnboost.cases.zeisel

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.aertslab.grnboost._
import org.aertslab.grnboost.cases.DataReader
import org.aertslab.grnboost.cases.zeisel.ZeiselReader.NR_META_FEATURES

/**
  * @author Thomas Moerman
  */
object ZeiselReaderOld {

  private[zeisel] val FEAT_INDEX_OFFSET = NR_META_FEATURES + 1
  private[zeisel] val OBSERVATION_INDEX_OFFSET  = 2

  /**
    * @param spark The SparkSession.
    * @param parquetFile The Zeisel parquet file.
    * @param rawFile The raw Zeisel data file.
    * @return Returns the Zeisel gene expression DataFrame from a parquet file.
    */
  def fromParquet(spark: SparkSession, parquetFile: String, rawFile: String): (DataFrame, List[Gene]) = {
    val df = spark.read.parquet(parquetFile).cache

    val genes = readGenes(spark, rawFile)

    (df, genes)
  }

  /**
    * @param spark The SparkSession.
    * @param raw The raw Zeisel file path.
    * @return Returns the List of Gene names.
    */
  private[zeisel] def readGenes(spark: SparkSession, raw: Path): List[Gene] = {

    // left of the separator Char
    def leftOf(c: Char)(s: String) = s.splitAt(s.indexOf(c))._1

    spark
      .sparkContext
      .textFile(raw)
      .zipWithIndex
      .filter{ case (_, idx) => idx >= FEAT_INDEX_OFFSET }
      .map{ case (s, _) => leftOf('\t')(s)}
      .collect
      .toList
  }


}