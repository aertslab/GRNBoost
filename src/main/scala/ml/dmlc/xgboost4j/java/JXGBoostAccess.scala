package ml.dmlc.xgboost4j.java

import ml.dmlc.xgboost4j.scala.XGBoostAccess.inner
import ml.dmlc.xgboost4j.scala.{DMatrix => ScalaDMatrix}
import org.aertslab.grnboost.BoosterParams

import scala.collection.JavaConverters._

/**
  * @author Thomas Moerman
  */
object JXGBoostAccess {

  /**
    * @param params
    * @param train
    * @param test
    * @return Returns a Booster instance.
    */
  def createBooster(params: BoosterParams, train: ScalaDMatrix, test: ScalaDMatrix): Booster = {
    val coerced: Map[String, Object] = params.mapValues(_.toString)

    new Booster(coerced.asJava, Array(inner(train), inner(test)))
  }

  def saveRabbitCheckpoint(booster: Booster): Unit = {
    booster.saveRabitCheckpoint()
  }

}