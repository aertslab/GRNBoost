package org.aertslab.grnboost

import com.holdenkarau.spark.testing.DataFrameSuiteBase
import ml.dmlc.xgboost4j.scala.Booster
import org.apache.spark.SparkConf
import org.scalatest.Suite

/**
  * Trait extending H. Karau's DataFrameSuiteBase, with conf overridden for GRNBoost.
  *
  * @author Thomas Moerman
  */
trait GRNBoostSuiteBase extends DataFrameSuiteBase { self: Suite =>

  // http://stackoverflow.com/questions/27220196/disable-scalatest-logging-statements-when-running-tests-from-maven
  try {
    org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
      .asInstanceOf[ch.qos.logback.classic.Logger]
      .setLevel(ch.qos.logback.classic.Level.INFO)
  } catch {
    case e: Throwable => Unit // nom nom nom
  }

  override def conf: SparkConf =
    new SparkConf()
      .setMaster("local[*]")
      .setAppName(GRN_BOOST)
      .set("spark.app.id", appID)

}